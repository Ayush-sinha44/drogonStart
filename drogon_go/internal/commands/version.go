package commands

import (
	"fmt"
	"runtime"

	"github.com/spf13/cobra"
)

var versionCmd = &cobra.Command{
	Use:   "version",
	Short: "display version of this tool",
	Run: func(cmd *cobra.Command, args []string) {
		banner := `     _
  __| |_ __ ___   __ _  ___  _ __
 / _` + "`" + ` | '__/ _ \ / _` + "`" + ` |/ _ \| '_ \
| (_| | | | (_) | (_| | (_) | | | |
 \__,_|_|  \___/ \__, |\___/|_| |_|
                 |___/`
		fmt.Println(banner)
		fmt.Println("A utility for drogon (Go reimplementation)")
		fmt.Println("Version: 0.1.0")
		fmt.Printf("Go version: %s\n", runtime.Version())
	},
}

func init() {
	rootCmd.AddCommand(versionCmd)
}
