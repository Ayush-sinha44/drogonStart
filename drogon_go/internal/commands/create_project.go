package commands

import (
	"fmt"
	"os"

	"github.com/AyushAgniworthy/drogon_ctl/internal/generator"
	"github.com/spf13/cobra"
)

var createProjectCmd = &cobra.Command{
	Use:   "project project_name",
	Short: "create a project",
	Run: func(cmd *cobra.Command, args []string) {
		if len(args) < 1 {
			fmt.Println("please input project name")
			os.Exit(1)
		}
		projectName := args[0]
		if err := generator.GenerateProject(projectName); err != nil {
			fmt.Fprintln(os.Stderr, err)
			os.Exit(1)
		}
	},
}

func init() {
	createCmd.AddCommand(createProjectCmd)
}
