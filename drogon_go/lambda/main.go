package main

import (
	"archive/zip"
	"context"
	"fmt"
	"io"
	"log"
	"os"
	"path/filepath"

	"github.com/aws/aws-lambda-go/lambda"
	"github.com/aws/aws-sdk-go-v2/aws"
	"github.com/aws/aws-sdk-go-v2/config"
	"github.com/aws/aws-sdk-go-v2/service/s3"
	"github.com/AyushAgniworthy/drogon_ctl/internal/generator"
)

type Request struct {
	ProjectName string `json:"projectName"`
	ProjectType string `json:"projectType"`
	JobId       string `json:"jobId"`
}

type Response struct {
	S3Key     string `json:"s3Key"`
	FileCount int    `json:"fileCount"`
}

func handleRequest(ctx context.Context, req Request) (Response, error) {
	log.Printf("Received request: %+v", req)

	if req.ProjectName == "" || req.JobId == "" {
		return Response{}, fmt.Errorf("projectName and jobId are required")
	}

	bucket := os.Getenv("SCAFFOLD_S3_BUCKET")
	if bucket == "" {
		return Response{}, fmt.Errorf("SCAFFOLD_S3_BUCKET environment variable is not set")
	}

	// Change to /tmp
	err := os.Chdir("/tmp")
	if err != nil {
		return Response{}, fmt.Errorf("failed to change directory to /tmp: %v", err)
	}

	// Clean up previously generated if any
	os.RemoveAll(req.ProjectName)
	zipFilename := req.ProjectName + "-base.zip"
	os.Remove(zipFilename)

	defer func() {
		os.RemoveAll(req.ProjectName)
		os.Remove(zipFilename)
	}()

	// Generate project
	log.Printf("Generating project %s...", req.ProjectName)
	err = generator.GenerateProject(req.ProjectName)
	if err != nil {
		return Response{}, fmt.Errorf("failed to generate project: %v", err)
	}

	// Zip the directory
	log.Printf("Zipping project...")
	zipFile, err := os.Create(zipFilename)
	if err != nil {
		return Response{}, fmt.Errorf("failed to create zip file: %v", err)
	}

	zipWriter := zip.NewWriter(zipFile)
	var fileCount int

	err = filepath.Walk(req.ProjectName, func(path string, info os.FileInfo, err error) error {
		if err != nil {
			return err
		}
		if info.IsDir() {
			return nil
		}
		fileCount++

		relPath := path
		zipEntry, err := zipWriter.Create(relPath)
		if err != nil {
			return err
		}
		fsFile, err := os.Open(path)
		if err != nil {
			return err
		}
		defer fsFile.Close()
		_, err = io.Copy(zipEntry, fsFile)
		return err
	})

	if err != nil {
		zipWriter.Close()
		zipFile.Close()
		return Response{}, fmt.Errorf("failed to zip directory: %v", err)
	}
	zipWriter.Close()
	zipFile.Close()

	// Upload to S3
	log.Printf("Uploading to S3...")
	cfg, err := config.LoadDefaultConfig(ctx)
	if err != nil {
		return Response{}, fmt.Errorf("failed to load AWS config: %v", err)
	}

	s3Client := s3.NewFromConfig(cfg)
	s3Key := fmt.Sprintf("jobs/%s/%s-base.zip", req.JobId, req.ProjectName)

	fileToUpload, err := os.Open(zipFilename)
	if err != nil {
		return Response{}, fmt.Errorf("failed to open zip file for upload: %v", err)
	}
	defer fileToUpload.Close()

	_, err = s3Client.PutObject(ctx, &s3.PutObjectInput{
		Bucket: aws.String(bucket),
		Key:    aws.String(s3Key),
		Body:   fileToUpload,
	})
	if err != nil {
		return Response{}, fmt.Errorf("failed to upload to S3: %v", err)
	}

	log.Printf("Upload complete. S3 Key: %s, File Count: %d", s3Key, fileCount)
	return Response{
		S3Key:     s3Key,
		FileCount: fileCount,
	}, nil
}

func main() {
	lambda.Start(handleRequest)
}
