package generator

import (
	"fmt"
	"os"
	"text/template"

	"github.com/AyushAgniworthy/drogon_ctl/internal/templates"
)

type FilterData struct {
	ClassName       string
	FileName        string
	Namespaces      []string
	NamespaceString string
}

func GenerateFilter(className string) error {
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

	if err := executeTemplate("filter_h.tmpl", headerFile, data); err != nil {
		return err
	}
	if err := executeTemplate("filter_cc.tmpl", sourceFile, data); err != nil {
		return err
	}

	fmt.Println("create a http filter:" + className)
	return nil
}

func executeTemplate(tmplName string, outputFile string, data interface{}) error {
	tmpl, err := template.ParseFS(templates.FS, tmplName)
	if err != nil {
		return err
	}
	f, err := os.Create(outputFile)
	if err != nil {
		return err
	}
	defer f.Close()

	return tmpl.Execute(f, data)
}
