package generator

import (
	"fmt"
)

func GeneratePlugin(className string) error {
	fileName := ClassToFileName(className)
	namespaces, bareClass := SplitNamespace(className)

	headerFile := fileName + ".h"
	sourceFile := fileName + ".cc"

	if err := CheckOverwrite(headerFile, sourceFile); err != nil {
		return err
	}

	data := FilterData{
		ClassName:       bareClass,
		FileName:        fileName,
		Namespaces:      namespaces,
		NamespaceString: NamespaceString(namespaces),
	}

	if err := executeTemplate("plugin_h.tmpl", headerFile, data); err != nil {
		return err
	}
	if err := executeTemplate("plugin_cc.tmpl", sourceFile, data); err != nil {
		return err
	}

	fmt.Println("create a plugin:" + className)
	return nil
}
