package generator

import (
	"bufio"
	"fmt"
	"os"
)

// CheckOverwrite checks if any of the provided files exist.
// If any exist, it prompts the user whether to overwrite.
// If the user chooses 'y' or 'Y', it returns nil.
// Otherwise, it prints "Abort!" and exits the program.
func CheckOverwrite(files ...string) error {
	return ForceCheckOverwrite(false, files...)
}

// ForceCheckOverwrite does the same as CheckOverwrite, but skips the prompt if force is true.
func ForceCheckOverwrite(force bool, files ...string) error {
	if force {
		return nil
	}

	anyExists := false
	for _, file := range files {
		if _, err := os.Stat(file); err == nil {
			anyExists = true
			break
		}
	}

	if !anyExists {
		return nil
	}

	fmt.Print("The file you want to create already exists, overwrite it(y/n)?")

	reader := bufio.NewReader(os.Stdin)
	char, _, err := reader.ReadRune()
	if err != nil {
		fmt.Println("\nAbort!")
		os.Exit(0)
	}

	if char != 'Y' && char != 'y' {
		fmt.Println("Abort!")
		os.Exit(0)
	}

	return nil
}
