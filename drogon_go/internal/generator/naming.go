package generator

import (
	"strings"
	"unicode"
)

// NameTransform replicates the C++ nameTransform function.
// Converts string to lowercase, splits on '_' or '.', and title-cases the first letter after each delimiter.
// If isType is true, it also capitalizes the first character.
func NameTransform(name string, isType bool) string {
	name = strings.ToLower(name)

	var result strings.Builder
	capitalizeNext := isType

	for _, ch := range name {
		if ch == '_' || ch == '.' {
			capitalizeNext = true
		} else {
			if capitalizeNext {
				result.WriteRune(unicode.ToUpper(ch))
				capitalizeNext = false
			} else {
				result.WriteRune(ch)
			}
		}
	}

	return result.String()
}

// SplitNamespace splits a class name into its namespaces and the bare class name.
func SplitNamespace(className string) ([]string, string) {
	parts := strings.Split(className, "::")
	if len(parts) == 1 {
		return []string{}, parts[0]
	}
	return parts[:len(parts)-1], parts[len(parts)-1]
}

// ClassToFileName replaces "::" with "_" in a class name.
func ClassToFileName(className string) string {
	return strings.ReplaceAll(className, "::", "_")
}

// NamespaceString joins a slice of namespaces with "::".
func NamespaceString(namespaces []string) string {
	return strings.Join(namespaces, "::")
}
