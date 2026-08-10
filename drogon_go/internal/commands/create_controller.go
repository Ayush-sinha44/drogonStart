package commands

import (
	"fmt"
	"os"

	"github.com/AyushAgniworthy/drogon_ctl/internal/generator"
	"github.com/spf13/cobra"
)

var (
	ctrlSimple    bool
	ctrlHttp      bool
	ctrlAlias     bool
	ctrlWebsocket bool
	ctrlRestful   bool
	ctrlResource  string
)

var createControllerCmd = &cobra.Command{
	Use:   "controller [flags] [namespace::]class_name...",
	Short: "create controller files",
	Run: func(cmd *cobra.Command, args []string) {
		if len(args) == 0 {
			fmt.Println(ARGS_ERROR_STR)
			return
		}

		// Determine controller type
		ctlType := generator.ControllerSimple
		if ctrlHttp || ctrlAlias {
			ctlType = generator.ControllerHttp
		} else if ctrlWebsocket {
			ctlType = generator.ControllerWebSocket
		} else if ctrlRestful {
			ctlType = generator.ControllerRestful
		}

		if ctlType == generator.ControllerRestful {
			// Restful: check for --resource, exactly one class name
			if len(args) > 1 {
				fmt.Fprintln(os.Stderr, "Too many parameters")
				os.Exit(1)
			}
			className := args[0]
			if err := generator.GenerateRestfulController(className, ctrlResource); err != nil {
				fmt.Fprintln(os.Stderr, err)
				os.Exit(1)
			}
		} else {
			// Check for stray flags in remaining args
			for _, arg := range args {
				if len(arg) > 0 && arg[0] == '-' {
					fmt.Println(ARGS_ERROR_STR)
					return
				}
			}
			for _, className := range args {
				if err := generator.GenerateController(className, ctlType); err != nil {
					fmt.Fprintln(os.Stderr, err)
					os.Exit(1)
				}
			}
		}
	},
}

func init() {
	createCmd.AddCommand(createControllerCmd)
	createControllerCmd.Flags().BoolVarP(&ctrlSimple, "simple", "s", false, "HttpSimpleController (default)")
	createControllerCmd.Flags().BoolVar(&ctrlHttp, "http", false, "HttpController")
	createControllerCmd.Flags().BoolVarP(&ctrlAlias, "http-alias", "a", false, "alias for --http")
	createControllerCmd.Flags().BoolVarP(&ctrlWebsocket, "websocket", "w", false, "WebSocketController")
	createControllerCmd.Flags().BoolVarP(&ctrlRestful, "restful", "r", false, "Restful controller")
	createControllerCmd.Flags().StringVar(&ctrlResource, "resource", "", "resource path (only with -r)")
}
