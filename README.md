# Drogon Start (Backend API)

![Java](https://img.shields.io/badge/Java-21-orange.svg)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-brightgreen.svg)
![AWS](https://img.shields.io/badge/AWS-Lambda%20%7C%20S3-blue.svg)
![PostgreSQL](https://img.shields.io/badge/Database-NeonDB-336791.svg)

This repository contains the Backend REST API for **Drogon Start**, a highly scalable SaaS application designed to seamlessly scaffold and generate [Drogon C++](https://github.com/drogonframework/drogon) projects. 

## 🏗️ Architecture overview

The backend acts as an asynchronous orchestrator, connecting the frontend user interface to heavy-duty code generation logic without blocking HTTP requests. 

1. **REST API**: Built with Spring Boot to accept configuration payloads from the React frontend.
2. **Compute Offloading**: Scaffolding logic (built in **Golang**) is offloaded to **AWS Lambda** to ensure the main API remains responsive under heavy load.
3. **Secure Delivery**: Generated projects are compressed and uploaded to **AWS S3**. The backend provisions a time-limited **Presigned URL** for secure client downloads.
4. **Data Tracking**: Connects to **NeonDB** (Serverless PostgreSQL) to persist job statuses, requested configurations, and generation metrics.
5. **Deployment**: Containerized via a `Dockerfile` and deployed on **Render**.

## 🚀 Features
- **Asynchronous Jobs**: Client polling mechanism to prevent HTTP timeouts during heavy generation.
- **Polyglot Execution**: Java-based orchestration triggering Golang-based AWS Lambda functions.
- **Dependency Customization**: Dynamic injection of C++ standards and Drogon versions via `CMakeLists.txt` patching.
- **Cloud-Native Storage**: Auto-expiring S3 bucket architecture for cost-efficient file delivery.

## 🛠️ Tech Stack
- **Framework**: Java 21 & Spring Boot 3
- **Cloud Provider**: AWS SDK (S3, Lambda)
- **Database**: PostgreSQL (NeonDB)
- **Containerization**: Docker
- **Deployment**: Render

## ⚙️ Environment Variables

To run this project locally or in production, you must configure the following environment variables:

| Variable | Description |
| :--- | :--- |
| `SPRING_PROFILES_ACTIVE` | Set to `render` for production, leave blank for local dev. |
| `DATABASE_URL` | JDBC URL for your PostgreSQL instance (e.g., NeonDB). |
| `DATABASE_USERNAME` | Database user. |
| `DATABASE_PASSWORD` | Database password. |
| `AWS_ACCESS_KEY_ID` | IAM User access key with S3/Lambda permissions. |
| `AWS_SECRET_ACCESS_KEY` | IAM User secret key. |
| `AWS_REGION` | AWS Region (e.g., `ap-south-1`). |
| `AWS_S3_BUCKET` | The name of the S3 bucket to store generated zips. |
| `AWS_LAMBDA_FUNCTION_NAME` | The exact name of your AWS Lambda function. |

## 💻 Running Locally

1. **Clone the repository:**
   ```bash
   git clone https://github.com/Ayush-sinha44/drogonStart.git
   cd drogonStart
   ```

2. **Configure your environment:**
   Create an `application-local.yml` or export the environment variables listed above into your terminal.

3. **Build and Run:**
   ```bash
   ./mvnw clean install -DskipTests
   ./mvnw spring-boot:run
   ```
   The API will be accessible at `http://localhost:8080`.

## 📦 Deployment

This backend is pre-configured for automated deployment on Render using Docker. 
1. Connect the repository to Render.
2. Select **Docker** as the runtime environment.
3. Add the required environment variables in the Render dashboard.
4. Deploy!
