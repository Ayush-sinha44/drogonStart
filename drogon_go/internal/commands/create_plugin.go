package commands

import (
	"fmt"
	"os"

	"github.com/AyushAgniworthy/drogon_ctl/internal/generator"
	"github.com/spf13/cobra"
)

var createPluginCmd = &cobra.Command{
	Use:   "plugin [namespace::]class_name...",
	Short: "create plugin class files",
	Run: func(cmd *cobra.Command, args []string) {
		if len(args) == 0 {
			fmt.Println("Invalid parameters!")
			return
		}
		for _, className := range args {
			if err := generator.GeneratePlugin(className); err != nil {
				fmt.Fprintln(os.Stderr, err)
				os.Exit(1)
			}
		}
	},
}

func init() {
	createCmd.AddCommand(createPluginCmd)
}
