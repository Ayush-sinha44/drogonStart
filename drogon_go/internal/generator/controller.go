package generator

import (
	"fmt"
	"os"
	"strings"
)

type ControllerType int

const (
	ControllerSimple ControllerType = iota
	ControllerHttp
	ControllerWebSocket
	ControllerRestful
)

type RestfulData struct {
	ClassName       string
	FileName        string
	Namespaces      []string
	NamespaceString string
	Resource        string
	CtlCommand      string
	Filters         string
	Indent          string
}

func GenerateController(className string, ctlType ControllerType) error {
	fileName := ClassToFileName(className)
	namespaces, bareClass := SplitNamespace(className)

	headerFile := fileName + ".h"
	sourceFile := fileName + ".cc"

	if err := CheckOverwrite(headerFile, sourceFile); err != nil {
		return err
	}

	var hContent, ccContent string

	switch ctlType {
	case ControllerSimple:
		hContent = buildSimpleHeader(bareClass, namespaces)
		ccContent = buildSimpleSource(bareClass, fileName, namespaces)
	case ControllerHttp:
		hContent = buildHttpHeader(bareClass, namespaces, className)
		ccContent = buildHttpSource(bareClass, fileName, namespaces)
	case ControllerWebSocket:
		hContent = buildWebSocketHeader(bareClass, namespaces)
		ccContent = buildWebSocketSource(bareClass, fileName, namespaces)
	default:
		return fmt.Errorf("unknown controller type")
	}

	if err := os.WriteFile(headerFile, []byte(hContent), 0644); err != nil {
		return err
	}
	if err := os.WriteFile(sourceFile, []byte(ccContent), 0644); err != nil {
		return err
	}

	switch ctlType {
	case ControllerSimple:
		fmt.Println("create a http simple controller:" + className)
	case ControllerHttp:
		fmt.Println("create a http controller:" + className)
	case ControllerWebSocket:
		fmt.Println("create a websocket controller:" + className)
	}

	return nil
}

func GenerateRestfulController(className string, resource string) error {
	fileName := ClassToFileName(className)
	namespaces, bareClass := SplitNamespace(className)

	headerFile := fileName + ".h"
	sourceFile := fileName + ".cc"

	if err := CheckOverwrite(headerFile, sourceFile); err != nil {
		return err
	}

	indent := strings.Repeat(" ", len(bareClass))

	data := RestfulData{
		ClassName:       bareClass,
		FileName:        fileName,
		Namespaces:      namespaces,
		NamespaceString: NamespaceString(namespaces),
		Resource:        resource,
		CtlCommand:      "create controller -r",
		Filters:         "",
		Indent:          indent,
	}

	if err := executeTemplate("restful_controller_h.tmpl", headerFile, data); err != nil {
		return err
	}
	if err := executeTemplate("restful_controller_cc.tmpl", sourceFile, data); err != nil {
		return err
	}

	fmt.Println("create a restful controller:" + className)
	return nil
}

func buildSimpleHeader(bareClass string, namespaces []string) string {
	var sb strings.Builder
	sb.WriteString("#pragma once\n\n")
	sb.WriteString("#include <drogon/HttpSimpleController.h>\n\n")
	sb.WriteString("using namespace drogon;\n\n")
	for _, ns := range namespaces {
		sb.WriteString("namespace " + ns + "\n{\n")
	}
	sb.WriteString("class " + bareClass + " : public drogon::HttpSimpleController<" + bareClass + ">\n{\n")
	sb.WriteString("  public:\n")
	sb.WriteString("    void asyncHandleHttpRequest(const HttpRequestPtr& req, std::function<void (const HttpResponsePtr &)> &&callback) override;\n")
	sb.WriteString("    PATH_LIST_BEGIN\n")
	sb.WriteString("    // list path definitions here;\n")
	sb.WriteString("    // PATH_ADD(\"/path\", \"filter1\", \"filter2\", HttpMethod1, HttpMethod2...);\n")
	sb.WriteString("    PATH_LIST_END\n")
	sb.WriteString("};\n")
	for i := len(namespaces) - 1; i >= 0; i-- {
		sb.WriteString("}\n")
	}
	return sb.String()
}

func buildSimpleSource(bareClass string, fileName string, namespaces []string) string {
	var sb strings.Builder
	sb.WriteString("#include \"" + fileName + ".h\"\n\n")
	nsStr := NamespaceString(namespaces)
	if nsStr != "" {
		sb.WriteString("using namespace " + nsStr + ";\n\n")
	}
	sb.WriteString("void " + bareClass + "::asyncHandleHttpRequest(const HttpRequestPtr& req, std::function<void (const HttpResponsePtr &)> &&callback)\n{\n")
	sb.WriteString("    // write your application logic here\n")
	sb.WriteString("}\n")
	return sb.String()
}

func buildHttpHeader(bareClass string, namespaces []string, className string) string {
	var sb strings.Builder
	sb.WriteString("#pragma once\n\n")
	sb.WriteString("#include <drogon/HttpController.h>\n\n")
	sb.WriteString("using namespace drogon;\n\n")
	for _, ns := range namespaces {
		sb.WriteString("namespace " + ns + "\n{\n")
	}
	sb.WriteString("class " + bareClass + " : public drogon::HttpController<" + bareClass + ">\n{\n")
	sb.WriteString("  public:\n")
	sb.WriteString("    METHOD_LIST_BEGIN\n")
	sb.WriteString("    // use METHOD_ADD to add your custom processing function here;\n")

	commentPath := "/"
	for _, ns := range namespaces {
		commentPath += ns + "/"
	}
	commentPath += bareClass

	sb.WriteString("    // METHOD_ADD(" + bareClass + "::get, \"/{2}/{1}\", Get); // path is " + commentPath + "/{arg2}/{arg1}\n")
	sb.WriteString("    // METHOD_ADD(" + bareClass + "::your_method_name, \"/{1}/{2}/list\", Get); // path is " + commentPath + "/{arg1}/{arg2}/list\n")
	sb.WriteString("    // ADD_METHOD_TO(" + bareClass + "::your_method_name, \"/absolute/path/{1}/{2}/list\", Get); // path is /absolute/path/{arg1}/{arg2}/list\n\n")
	sb.WriteString("    METHOD_LIST_END\n")
	sb.WriteString("    // your declaration of processing function maybe like this:\n")
	sb.WriteString("    // void get(const HttpRequestPtr& req, std::function<void (const HttpResponsePtr &)> &&callback, int p1, std::string p2);\n")
	sb.WriteString("    // void your_method_name(const HttpRequestPtr& req, std::function<void (const HttpResponsePtr &)> &&callback, double p1, int p2) const;\n")
	sb.WriteString("};\n")
	for i := len(namespaces) - 1; i >= 0; i-- {
		sb.WriteString("}\n")
	}
	return sb.String()
}

func buildHttpSource(bareClass string, fileName string, namespaces []string) string {
	var sb strings.Builder
	sb.WriteString("#include \"" + fileName + ".h\"\n\n")
	nsStr := NamespaceString(namespaces)
	if nsStr != "" {
		sb.WriteString("using namespace " + nsStr + ";\n\n")
	}
	sb.WriteString("// Add definition of your processing function here\n")
	return sb.String()
}

func buildWebSocketHeader(bareClass string, namespaces []string) string {
	var sb strings.Builder
	sb.WriteString("#pragma once\n\n")
	sb.WriteString("#include <drogon/WebSocketController.h>\n\n")
	sb.WriteString("using namespace drogon;\n\n")
	for _, ns := range namespaces {
		sb.WriteString("namespace " + ns + "\n{\n")
	}
	sb.WriteString("class " + bareClass + " : public drogon::WebSocketController<" + bareClass + ">\n{\n")
	sb.WriteString("  public:\n")
	sb.WriteString("     void handleNewMessage(const WebSocketConnectionPtr&,\n")
	sb.WriteString("                                  std::string &&,\n")
	sb.WriteString("                                  const WebSocketMessageType &) override;\n")
	sb.WriteString("    void handleNewConnection(const HttpRequestPtr &,\n")
	sb.WriteString("                                     const WebSocketConnectionPtr&) override;\n")
	sb.WriteString("    void handleConnectionClosed(const WebSocketConnectionPtr&) override;\n")
	sb.WriteString("    WS_PATH_LIST_BEGIN\n")
	sb.WriteString("    // list path definitions here;\n")
	sb.WriteString("    // WS_PATH_ADD(\"/path\", \"filter1\", \"filter2\", ...);\n")
	sb.WriteString("    WS_PATH_LIST_END\n")
	sb.WriteString("};\n")
	for i := len(namespaces) - 1; i >= 0; i-- {
		sb.WriteString("}\n")
	}
	return sb.String()
}

func buildWebSocketSource(bareClass string, fileName string, namespaces []string) string {
	var sb strings.Builder
	sb.WriteString("#include \"" + fileName + ".h\"\n\n")
	nsStr := NamespaceString(namespaces)
	if nsStr != "" {
		sb.WriteString("using namespace " + nsStr + ";\n\n")
	}
	sb.WriteString("void " + bareClass + "::handleNewMessage(const WebSocketConnectionPtr& wsConnPtr, std::string &&message, const WebSocketMessageType &type)\n{\n")
	sb.WriteString("    // write your application logic here\n")
	sb.WriteString("}\n\n")
	sb.WriteString("void " + bareClass + "::handleNewConnection(const HttpRequestPtr &req, const WebSocketConnectionPtr& wsConnPtr)\n{\n")
	sb.WriteString("    // write your application logic here\n")
	sb.WriteString("}\n\n")
	sb.WriteString("void " + bareClass + "::handleConnectionClosed(const WebSocketConnectionPtr& wsConnPtr)\n{\n")
	sb.WriteString("    // write your application logic here\n")
	sb.WriteString("}\n")
	return sb.String()
}
