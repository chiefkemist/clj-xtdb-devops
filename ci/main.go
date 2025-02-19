// A generated module for CljXtdbDevops functions
//
// This module has been generated via dagger init and serves as a reference to
// basic module structure as you get started with Dagger.
//
// Two functions have been pre-created. You can modify, delete, or add to them,
// as needed. They demonstrate usage of arguments and return types using simple
// echo and grep commands. The functions can be called from the dagger CLI or
// from one of the SDKs.
//
// The first line in this comment block is a short description line and the
// rest is a long description with more detail on the module's purpose or usage,
// if appropriate. All modules should have a short description.

package main

import (
	"context"
	"dagger/clj-xtdb-devops/internal/dagger"
	"fmt"
	"log"
	"time"
)

type CljXtdbDevops struct{}

func (m *CljXtdbDevops) BuildCljWebApp(srcDir *dagger.Directory) *dagger.Container {
	fmt.Println("🔨 Building Clojure web application...")
	buildStage := dag.Container().From("clojure:openjdk-17").
		WithMountedDirectory("/app", srcDir).
		WithWorkdir("/app").
		WithExec([]string{"clojure", "-T:build", "jar"})

	fmt.Println("📦 Creating JAR file...")
	jarFile := buildStage.File("target/my_app.jar")

	fmt.Println("🚀 Preparing runtime container...")
	return dag.Container().From("openjdk:20-slim").
		WithExec([]string{"mkdir", "-p", "/app/target"}).
		WithFile("/app/target/my_app.jar", jarFile).
		WithExposedPort(58950).
		WithEntrypoint([]string{"java", "-jar", "/app/target/my_app.jar"})
}

// PublishCljWebApp publishes the Clojure web application container
func (m *CljXtdbDevops) PublishCljWebApp(container *dagger.Container, tag string) (string, error) {
	return container.Publish(context.Background(), tag)
}

// BuildAndPublishCljWebApp combines building and publishing
func (m *CljXtdbDevops) BuildAndPublishCljWebApp(srcDir *dagger.Directory) {
	webApp := m.BuildCljWebApp(srcDir)

	// Publish image
	publishedImage, err := m.PublishCljWebApp(webApp, "ttl.sh/my-app:2h")
	if err != nil {
		log.Fatal(err)
	}
	fmt.Printf("Successfully published image: %s\n", publishedImage)
}

// BuildXTDB creates an XTDB container
func (m *CljXtdbDevops) BuildXTDB() *dagger.Container {
	fmt.Println("🏗️  Creating XTDB container...")
	return dag.Container().From("ghcr.io/xtdb/xtdb:2.0.0-beta6").
		WithEnvVariable("POSTGRES_USER", "postgres").
		WithEnvVariable("POSTGRES_PASSWORD", "postgres").
		WithEnvVariable("POSTGRES_DB", "postgres").
		WithEnvVariable("XTDB_ENABLE_POSTGRESQL", "true").
		WithEnvVariable("XTDB_POSTGRESQL_SCHEMA_TX_LOG", "xtdb_tx_log").
		WithEnvVariable("XTDB_POSTGRESQL_SCHEMA_DOC_STORE", "xtdb_docs").
		WithEnvVariable("XTDB_POSTGRESQL_POOL_SIZE", "20").
		WithEnvVariable("XTDB_ENABLE_QUERY_CACHE", "true").
		WithEnvVariable("XTDB_QUERY_CACHE_SIZE", "10000").
		WithExposedPort(3000). // HTTP API
		WithExposedPort(5432)  // PostgreSQL
}

// BuildPgAdmin creates a pgAdmin container
func (m *CljXtdbDevops) BuildPgAdmin() *dagger.Container {
	fmt.Println("🏗️  Creating pgAdmin container...")
	return dag.Container().From("dpage/pgadmin4:latest").
		WithEnvVariable("PGADMIN_DEFAULT_EMAIL", "admin@admin.com").
		WithEnvVariable("PGADMIN_DEFAULT_PASSWORD", "admin").
		WithExposedPort(80)
}

// RunLocalDevelopment spins up both XTDB and pgAdmin containers
func (m *CljXtdbDevops) RunLocalDevelopment(ctx context.Context) *dagger.Service {
	fmt.Println("🚀 Starting local development environment...")

	fmt.Println("📦 Building XTDB container...")
	xtdb := m.BuildXTDB().
		WithExposedPort(3000). // HTTP API
		WithExposedPort(5432). // PostgreSQL
		AsService()

	fmt.Println("📦 Building pgAdmin container...")
	pgAdmin := m.BuildPgAdmin().
		WithExposedPort(80).
		AsService()

	fmt.Println("🔄 Starting XTDB service...")
	if _, err := xtdb.Start(ctx); err != nil {
		log.Fatalf("❌ failed to start XTDB: %v", err)
	}
	fmt.Println("✅ XTDB service started successfully")

	fmt.Println("🔄 Starting pgAdmin service...")
	pgAdminService, err := pgAdmin.Start(ctx)
	if err != nil {
		log.Fatalf("❌ failed to start pgAdmin: %v", err)
	}
	fmt.Println("✅ pgAdmin service started successfully")

	fmt.Println("🎉 Local development environment ready!")
	fmt.Println("📝 Access points:")
	fmt.Println("  - XTDB HTTP API: http://localhost:3000")
	fmt.Println("  - XTDB PostgreSQL: localhost:5432")
	fmt.Println("  - pgAdmin: http://localhost:8080")
	fmt.Println("    - Email: admin@admin.com")
	fmt.Println("    - Password: admin")

	return pgAdminService
}

// RunLocalWebApp runs the Clojure web application locally with XTDB
func (m *CljXtdbDevops) RunLocalWebApp(ctx context.Context, srcDir *dagger.Directory) *dagger.Service {
	fmt.Println("🚀 Starting local web application environment...")

	fmt.Println("📦 Building XTDB container...")
	xtdb := m.BuildXTDB().
		WithExposedPort(3000).
		WithExposedPort(5432).
		AsService()

	fmt.Println("🔄 Starting XTDB service...")
	if _, err := xtdb.Start(ctx); err != nil {
		log.Fatalf("❌ failed to start XTDB: %v", err)
	}
	fmt.Println("✅ XTDB service started successfully")
	fmt.Println("⏳ Waiting for XTDB to be ready...")
	time.Sleep(5 * time.Second)

	fmt.Println("📦 Building web application...")
	webApp := m.BuildCljWebApp(srcDir).
		WithExposedPort(58950).
		WithEnvVariable("XTDB_HOST", "xtdb").
		WithServiceBinding("xtdb", xtdb).
		AsService()

	fmt.Println("🔄 Starting web application service...")
	webAppService, err := webApp.Start(ctx)
	if err != nil {
		log.Fatalf("❌ failed to start web application: %v", err)
	}
	fmt.Println("✅ Web application service started successfully")

	fmt.Println("🎉 Local web application environment ready!")
	fmt.Println("📝 Access points:")
	fmt.Println("  - Web Application: http://localhost:58950")
	fmt.Println("  - XTDB HTTP API: http://localhost:3000")
	fmt.Println("  - XTDB PostgreSQL: localhost:5432")

	return webAppService
}

// Returns a container that echoes whatever string argument is provided
func (m *CljXtdbDevops) ContainerEcho(stringArg string) *dagger.Container {
	return dag.Container().From("alpine:latest").WithExec([]string{"echo", stringArg})
}

// Returns lines that match a pattern in the files of the provided Directory
func (m *CljXtdbDevops) GrepDir(ctx context.Context, directoryArg *dagger.Directory, pattern string) (string, error) {
	return dag.Container().
		From("alpine:latest").
		WithMountedDirectory("/mnt", directoryArg).
		WithWorkdir("/mnt").
		WithExec([]string{"grep", "-R", pattern, "."}).
		Stdout(ctx)
}
