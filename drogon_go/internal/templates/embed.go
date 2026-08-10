package templates

import (
	"embed"
)

//go:embed *.tmpl
var FS embed.FS

// Load loads a template by name
func Load(name string) (string, error) {
	bytes, err := FS.ReadFile(name)
	if err != nil {
		return "", err
	}
	return string(bytes), nil
}

// MustLoad loads a template by name and panics on error
func MustLoad(name string) string {
	bytes, err := FS.ReadFile(name)
	if err != nil {
		panic(err)
	}
	return string(bytes)
}
