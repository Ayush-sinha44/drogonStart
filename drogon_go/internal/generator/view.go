package generator

import (
	"fmt"
	"os"
	"path/filepath"
	"regexp"
	"strings"
)

type ViewOptions struct {
	OutputPath      string
	Namespaces      []string
	PathToNamespace bool
}

func CompileCSP(cspFilename string, opts ViewOptions) error {
	return GenerateView(cspFilename, opts)
}

func GenerateView(cspFilename string, opts ViewOptions) error {
	contentBytes, err := os.ReadFile(cspFilename)
	if err != nil {
		return err
	}
	content := string(contentBytes)
	content = strings.ReplaceAll(content, "\r\n", "\n")
	content = strings.ReplaceAll(content, "\r", "")

	// Extract layout
	var layoutName string
	layoutRegex := regexp.MustCompile(`(?s)<%layout[ \t]+([^%]*?)[ \t]*%>`)
	if match := layoutRegex.FindStringSubmatch(content); len(match) > 1 {
		layoutName = strings.TrimSpace(match[1])
	}
	content = layoutRegex.ReplaceAllString(content, "")

	// Pre-process {% expr %}
	exprRegex := regexp.MustCompile(`(?s)\{%[ \t]*(.*?)[ \t]*%\}`)
	content = exprRegex.ReplaceAllString(content, "<%c++$$$$<<$1;%>")

	// Determine class name and namespaces
	baseName := filepath.Base(cspFilename)
	ext := filepath.Ext(baseName)
	className := strings.TrimSuffix(baseName, ext)

	namespaces := make([]string, len(opts.Namespaces))
	copy(namespaces, opts.Namespaces)

	if opts.PathToNamespace {
		dir := filepath.Dir(cspFilename)
		dir = strings.TrimPrefix(dir, ".")
		dir = strings.TrimPrefix(dir, "/")
		if dir != "" && dir != "." {
			parts := strings.Split(dir, string(filepath.Separator))
			for _, p := range parts {
				if p != "" {
					namespaces = append(namespaces, p)
				}
			}
		}
	}

	prefix := ""
	if len(namespaces) > 0 {
		prefix = strings.Join(namespaces, "_") + "_"
	}

	streamName := className + "_tmp_stream"
	viewDataName := className + "_view_data"

	var out strings.Builder
	var incOut strings.Builder

	cxxFlag := 0
	lines := strings.Split(content, "\n")

	// drogon doesn't process the last empty element if ends with \n
	if len(lines) > 0 && lines[len(lines)-1] == "" {
		lines = lines[:len(lines)-1]
	}

	for _, line := range lines {
		parseLine(line, streamName, viewDataName, &cxxFlag, &out, &incOut, true)
	}

	outPath := opts.OutputPath
	if outPath == "" {
		outPath = "."
	}
	if err := os.MkdirAll(outPath, 0755); err != nil {
		return err
	}

	hContent := generateHeader(className, namespaces)
	ccContent := generateSource(className, prefix, layoutName, namespaces, incOut.String(), out.String())

	hPath := filepath.Join(outPath, prefix+className+".h")
	ccPath := filepath.Join(outPath, prefix+className+".cc")

	if err := os.WriteFile(hPath, []byte(hContent), 0644); err != nil {
		return err
	}
	if err := os.WriteFile(ccPath, []byte(ccContent), 0644); err != nil {
		return err
	}

	return nil
}

func parseLine(s string, streamName, viewDataName string, cxxFlag *int, out, incOut *strings.Builder, isEndOfLine bool) {
	if *cxxFlag == 0 {
		idxInc := strings.Index(strings.ToLower(s), "<%inc")
		idxCxx := strings.Index(s, "<%c++")
		idxView := strings.Index(s, "<%view")
		idxVal := strings.Index(s, "[[")

		minIdx := -1
		tagType := ""

		check := func(idx int, t string) {
			if idx >= 0 {
				if minIdx == -1 || idx < minIdx {
					minIdx = idx
					tagType = t
				}
			}
		}
		check(idxInc, "inc")
		check(idxCxx, "cxx")
		check(idxView, "view")
		check(idxVal, "val")

		if minIdx == -1 {
			if len(s) > 0 {
				escaped := strings.ReplaceAll(s, "\\", "\\\\")
				escaped = strings.ReplaceAll(escaped, "\"", "\\\"")
				if isEndOfLine {
					out.WriteString(fmt.Sprintf("\t%s << \"%s\\n\";\n", streamName, escaped))
				} else {
					out.WriteString(fmt.Sprintf("\t%s << \"%s\";\n", streamName, escaped))
				}
			} else {
				if isEndOfLine {
					out.WriteString(fmt.Sprintf("\t%s << \"\\n\";\n", streamName))
				}
			}
			return
		}

		if minIdx > 0 {
			escaped := strings.ReplaceAll(s[:minIdx], "\\", "\\\\")
			escaped = strings.ReplaceAll(escaped, "\"", "\\\"")
			out.WriteString(fmt.Sprintf("\t%s << \"%s\";\n", streamName, escaped))
		}

		rem := s[minIdx:]
		if tagType == "inc" {
			*cxxFlag = 2
			parseLine(rem[5:], streamName, viewDataName, cxxFlag, out, incOut, isEndOfLine)
		} else if tagType == "cxx" {
			*cxxFlag = 1
			parseLine(rem[5:], streamName, viewDataName, cxxFlag, out, incOut, isEndOfLine)
		} else if tagType == "view" {
			endIdx := strings.Index(rem, "%>")
			var viewName string
			var next string
			if endIdx == -1 {
				viewName = strings.TrimSpace(rem[6:])
				next = ""
			} else {
				viewName = strings.TrimSpace(rem[6:endIdx])
				next = rem[endIdx+2:]
			}
			out.WriteString(fmt.Sprintf("{\n    auto templ=DrTemplateBase::newTemplate(\"%s\");\n    if(templ){\n      %s<< templ->genText(%s);\n    }\n}\n", viewName, streamName, viewDataName))
			parseLine(next, streamName, viewDataName, cxxFlag, out, incOut, isEndOfLine)
		} else if tagType == "val" {
			endIdx := strings.Index(rem, "]]")
			var keyName string
			var next string
			if endIdx == -1 {
				keyName = strings.TrimSpace(rem[2:])
				next = ""
			} else {
				keyName = strings.TrimSpace(rem[2:endIdx])
				next = rem[endIdx+2:]
			}
			out.WriteString(fmt.Sprintf("{\n    auto & val=%s[\"%s\"];\n    if(val.type()==typeid(const char *)){\n        %s<<*(std::any_cast<const char *>(&val));\n    }else if(val.type()==typeid(std::string)||val.type()==typeid(const std::string)){\n        %s<<*(std::any_cast<const std::string>(&val));\n    }\n}\n", viewDataName, keyName, streamName, streamName))
			parseLine(next, streamName, viewDataName, cxxFlag, out, incOut, isEndOfLine)
		}
	} else if *cxxFlag == 1 { // cxx
		idx := strings.Index(s, "%>")
		if idx == -1 {
			code := processCxx(s, streamName, viewDataName)
			out.WriteString(code + "\n")
			return
		}
		code := processCxx(s[:idx], streamName, viewDataName)
		out.WriteString(code)
		*cxxFlag = 0
		parseLine(s[idx+2:], streamName, viewDataName, cxxFlag, out, incOut, isEndOfLine)
	} else if *cxxFlag == 2 { // inc
		idx := strings.Index(s, "%>")
		if idx == -1 {
			incOut.WriteString(s + "\n")
			return
		}
		incOut.WriteString(s[:idx] + "\n")
		*cxxFlag = 0
		parseLine(s[idx+2:], streamName, viewDataName, cxxFlag, out, incOut, isEndOfLine)
	}
}

func processCxx(s, streamName, viewDataName string) string {
	s = strings.ReplaceAll(s, "$$", streamName)
	s = strings.ReplaceAll(s, "@@", viewDataName)
	return s
}

func generateHeader(className string, namespaces []string) string {
	var sb strings.Builder
	sb.WriteString("//this file is generated by program automatically,don't modify it!\n")
	sb.WriteString("#include <drogon/DrTemplate.h>\n")
	for _, ns := range namespaces {
		sb.WriteString(fmt.Sprintf("namespace %s\n{\n", ns))
	}
	sb.WriteString(fmt.Sprintf("class %s:public drogon::DrTemplate<%s>\n{\npublic:\n\t%s(){};\n\tvirtual ~%s(){};\n\tvirtual std::string genText(const drogon::DrTemplateData &) override;\n};\n", className, className, className, className))
	for i := len(namespaces) - 1; i >= 0; i-- {
		sb.WriteString("}\n")
	}
	return sb.String()
}

func generateSource(className, prefix, layoutName string, namespaces []string, incContent, processedContent string) string {
	var sb strings.Builder
	sb.WriteString("//this file is generated by program(drogon_ctl) automatically,don't modify it!\n")
	sb.WriteString(fmt.Sprintf("#include \"%s%s.h\"\n", prefix, className))
	sb.WriteString("#include <drogon/utils/OStringStream.h>\n")
	sb.WriteString("#include <drogon/utils/Utilities.h>\n")
	sb.WriteString("#include <string>\n#include <map>\n#include <vector>\n#include <set>\n#include <iostream>\n#include <unordered_map>\n#include <unordered_set>\n#include <algorithm>\n#include <list>\n#include <deque>\n#include <queue>\n")

	if incContent != "" {
		sb.WriteString(incContent)
		if !strings.HasSuffix(incContent, "\n") {
			sb.WriteString("\n")
		}
	}

	if len(namespaces) > 0 {
		sb.WriteString(fmt.Sprintf("using namespace %s;\n", strings.Join(namespaces, "::")))
	}
	sb.WriteString("using namespace drogon;\n")

	sb.WriteString(fmt.Sprintf("std::string %s::genText(const DrTemplateData& %s_view_data)\n{\n", className, className))
	sb.WriteString(fmt.Sprintf("\tdrogon::OStringStream %s_tmp_stream;\n", className))
	sb.WriteString(fmt.Sprintf("\tstd::string layoutName{\"%s\"};\n", layoutName))

	sb.WriteString(processedContent)

	sb.WriteString("if(layoutName.empty())\n{\nstd::string ret{std::move(")
	sb.WriteString(className)
	sb.WriteString("_tmp_stream.str())};\nreturn ret;\n}else\n{\nauto templ = DrTemplateBase::newTemplate(layoutName);\nif(!templ) return \"\";\nHttpViewData data = ")
	sb.WriteString(className)
	sb.WriteString("_view_data;\nauto str = std::move(")
	sb.WriteString(className)
	sb.WriteString("_tmp_stream.str());\nif(!str.empty() && str[str.length()-1] == '\\n') str.resize(str.length()-1);\ndata[\"\"] = std::move(str);\nreturn templ->genText(data);\n}\n}\n")

	return sb.String()
}
