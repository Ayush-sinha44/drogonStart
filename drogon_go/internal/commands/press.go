package commands

import (
	"fmt"

	"github.com/spf13/cobra"
)

var (
	pressNumReqs     int
	pressNumThreads  int
	pressConcurrency int
	pressDisableSSL  bool
	pressCustomJSON  string
	pressQuiet       bool
)

var pressCmd = &cobra.Command{
	Use:   "press",
	Short: "Do stress testing...",
	Run: func(cmd *cobra.Command, args []string) {
		if len(args) == 0 {
			fmt.Println("Invalid parameters!")
			return
		}
		fmt.Println("Not yet implemented")
	},
}

func init() {
	rootCmd.AddCommand(pressCmd)
	pressCmd.Flags().IntVarP(&pressNumReqs, "num-reqs", "n", 1, "number of requests")
	pressCmd.Flags().IntVarP(&pressNumThreads, "num-threads", "t", 1, "number of threads")
	pressCmd.Flags().IntVarP(&pressConcurrency, "concurrency", "c", 1, "concurrent connections")
	pressCmd.Flags().BoolVarP(&pressDisableSSL, "disable-ssl", "k", false, "disable SSL cert validation")
	pressCmd.Flags().StringVarP(&pressCustomJSON, "custom-json", "f", "", "custom request JSON file")
	pressCmd.Flags().BoolVarP(&pressQuiet, "quiet", "q", false, "quiet mode")
}
