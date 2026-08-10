package generator

import (
	"fmt"
	"os"
	"path/filepath"
)

type ProjectData struct {
	ProjectName string
}

func GenerateProject(projectName string) error {
	if _, err := os.Stat(projectName); !os.IsNotExist(err) {
		return fmt.Errorf("The directory already exists, please use another project name!")
	}

	dirs := []string{
		projectName,
		filepath.Join(projectName, "views"),
		filepath.Join(projectName, "controllers"),
		filepath.Join(projectName, "filters"),
		filepath.Join(projectName, "plugins"),
		filepath.Join(projectName, "build"),
		filepath.Join(projectName, "models"),
		filepath.Join(projectName, "test"),
	}

	for _, d := range dirs {
		if err := os.MkdirAll(d, 0755); err != nil {
			return err
		}
	}

	data := ProjectData{ProjectName: projectName}

	filesToGenerate := map[string]string{
		"cmake.tmpl":       "CMakeLists.txt",
		"demo_main.tmpl":   "main.cc",
		"gitignore.tmpl":   ".gitignore",
		"config_json.tmpl": "config.json",
		"config_yaml.tmpl": "config.yaml",
		"model_json.tmpl":  filepath.Join("models", "model.json"),
		"test_main.tmpl":   filepath.Join("test", "test_main.cc"),
		"test_cmake.tmpl":  filepath.Join("test", "CMakeLists.txt"),
	}

	for tmpl, out := range filesToGenerate {
		if err := executeTemplate(tmpl, filepath.Join(projectName, out), data); err != nil {
			return err
		}
	}

	fmt.Printf("create a project named %s\n", projectName)
	return nil
}
