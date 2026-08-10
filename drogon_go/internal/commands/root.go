package commands

import (
	"fmt"
	"os"

	"github.com/spf13/cobra"
)

const ARGS_ERROR_STR = "args error!use help command to get usage!"

var rootCmd = &cobra.Command{
	Use:   "drogon_ctl",
	Short: "drogon_ctl",
	Run: func(cmd *cobra.Command, args []string) {
		versionFlag, _ := cmd.Flags().GetBool("version")
		if versionFlag {
			versionCmd.Run(cmd, args)
			return
		}
		cmd.Help()
	},
}

func Execute() {
	if err := rootCmd.Execute(); err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
}

func init() {
	rootCmd.Flags().BoolP("version", "v", false, "display version of this tool")
	// Custom help function to match original drogon_ctl format exactly
	rootCmd.SetHelpFunc(func(cmd *cobra.Command, args []string) {
		fmt.Println("usage: drogon_ctl [-v | --version] [-h | --help] <command> [<args>]")
		fmt.Println("commands list:")
		fmt.Printf("%-24s%s\n", "create", "create some source files(Use 'drogon_ctl help create' for more information)")
		fmt.Printf("%-24s%s\n", "help", "display this message")
		fmt.Printf("%-24s%s\n", "press", "Do stress testing...")
		fmt.Printf("%-24s%s\n", "version", "display version of this tool")
	})
}
