package generator

import (
	"reflect"
	"testing"
)

func TestNameTransform(t *testing.T) {
	tests := []struct {
		input  string
		isType bool
		want   string
	}{
		{"user_roles", true, "UserRoles"},
		{"user_roles", false, "userRoles"},
		{"simple", true, "Simple"},
		{"my.table.name", true, "MyTableName"},
		{"UPPER_CASE", true, "UpperCase"},
		{"already_Good", false, "alreadyGood"},
	}

	for _, tt := range tests {
		got := NameTransform(tt.input, tt.isType)
		if got != tt.want {
			t.Errorf("NameTransform(%q, %v) = %q; want %q", tt.input, tt.isType, got, tt.want)
		}
	}
}

func TestSplitNamespace(t *testing.T) {
	tests := []struct {
		input          string
		wantNamespaces []string
		wantBareClass  string
	}{
		{"api::v1::MyClass", []string{"api", "v1"}, "MyClass"},
		{"MyClass", []string{}, "MyClass"},
	}

	for _, tt := range tests {
		gotNamespaces, gotBareClass := SplitNamespace(tt.input)
		if !reflect.DeepEqual(gotNamespaces, tt.wantNamespaces) {
			if len(gotNamespaces) != 0 || len(tt.wantNamespaces) != 0 {
				t.Errorf("SplitNamespace(%q) namespaces = %v; want %v", tt.input, gotNamespaces, tt.wantNamespaces)
			}
		}
		if gotBareClass != tt.wantBareClass {
			t.Errorf("SplitNamespace(%q) bareClass = %q; want %q", tt.input, gotBareClass, tt.wantBareClass)
		}
	}
}

func TestClassToFileName(t *testing.T) {
	tests := []struct {
		input string
		want  string
	}{
		{"api::v1::MyClass", "api_v1_MyClass"},
		{"MyClass", "MyClass"},
	}

	for _, tt := range tests {
		got := ClassToFileName(tt.input)
		if got != tt.want {
			t.Errorf("ClassToFileName(%q) = %q; want %q", tt.input, got, tt.want)
		}
	}
}

func TestNamespaceString(t *testing.T) {
	tests := []struct {
		input []string
		want  string
	}{
		{[]string{"api", "v1"}, "api::v1"},
		{[]string{}, ""},
		{nil, ""},
	}

	for _, tt := range tests {
		got := NamespaceString(tt.input)
		if got != tt.want {
			t.Errorf("NamespaceString(%v) = %q; want %q", tt.input, got, tt.want)
		}
	}
}
