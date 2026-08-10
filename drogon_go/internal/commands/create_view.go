package commands

import (
	"fmt"
	"os"
	"strings"

	"github.com/AyushAgniworthy/drogon_ctl/internal/generator"
	"github.com/spf13/cobra"
)

var (
	viewOutput          string
	viewNamespace       string
	viewPathToNamespace bool
)

var createViewCmd = &cobra.Command{
	Use:   "view csp_file_name...",
	Short: "create view class files",
	Run: func(cmd *cobra.Command, args []string) {
		if len(args) == 0 {
			fmt.Println(ARGS_ERROR_STR)
			return
		}

		var namespaces []string
		if viewNamespace != "" {
			namespaces = strings.Split(viewNamespace, "::")
		}

		opts := generator.ViewOptions{
			OutputPath:      viewOutput,
			Namespaces:      namespaces,
			PathToNamespace: viewPathToNamespace,
		}

		for _, cspFile := range args {
			fmt.Printf("create view:%s\n", cspFile)
			if err := generator.GenerateView(cspFile, opts); err != nil {
				fmt.Fprintln(os.Stderr, err)
				os.Exit(1)
			}
		}
	},
}

func init() {
	createCmd.AddCommand(createViewCmd)
	createViewCmd.Flags().StringVarP(&viewOutput, "output", "o", ".", "output path")
	createViewCmd.Flags().StringVarP(&viewNamespace, "namespace", "n", "", "namespace")
	createViewCmd.Flags().BoolVar(&viewPathToNamespace, "path-to-namespace", false, "derive namespace from file path")
}
