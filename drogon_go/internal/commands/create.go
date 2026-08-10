package commands

import (
	"fmt"

	"github.com/spf13/cobra"
)

var createCmd = &cobra.Command{
	Use:   "create",
	Short: "create some source files(Use 'drogon_ctl help create' for more information)",
	Run: func(cmd *cobra.Command, args []string) {
		cmd.Help()
	},
}

func init() {
	rootCmd.AddCommand(createCmd)
	createCmd.SetHelpFunc(func(cmd *cobra.Command, args []string) {
		fmt.Println(`Use create command to create some source files of drogon webapp

Usage:drogon_ctl create <view|controller|filter|project|model> [-options] <object name>

drogon_ctl create view <csp file name> [-o <output path>] [-n <namespace>] [--path-to-namespace] //create HttpView source files from csp files, namespace is prefixed of path-to-namespace

drogon_ctl create controller [-s] <[namespace::]class_name> //create HttpSimpleController source files

drogon_ctl create controller -a <[namespace::]class_name> //create HttpController source files

drogon_ctl create controller -w <[namespace::]class_name> //create WebSocketController source files

drogon_ctl create controller -r <[namespace::]class_name> //create Http restful API controller source files

drogon_ctl create filter <[namespace::]class_name> //create a filter named class_name

drogon_ctl create plugin <[namespace::]class_name> //create a plugin named class_name

drogon_ctl create project <project_name> //create a project named project_name

drogon_ctl create model <model path> //create model classes in model path`)
	})
}
