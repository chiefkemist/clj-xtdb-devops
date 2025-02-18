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
	"runtime"
)

type CljXtdbDevops struct{}

// BuildCljWebApp builds and exports a container image for the Clojure web application.
// It accepts a dagger.Directory with the source code, uses a Linux/amd64 platform,
// and configures the container by:
//   - Mounting the source directory at "/app"
//   - Setting the working directory to "/app"
//   - Exposing port 58950
//   - Executing "clojure -M -m my-app.handler"
//
// Upon successful build, the container image is exported to "my-app-image.tar".
func (m *CljXtdbDevops) BuildCljWebApp(srcDir *dagger.Directory) {
	// Define a helper function to perform the build and publish stages.
	buildAndPublish := func(opts dagger.ContainerOpts, publishTag string, ctx context.Context) (string, error) {
		buildStage := dag.Container(opts).From("clojure:openjdk-17").
			WithMountedDirectory("/app", srcDir).
			WithWorkdir("/app").
			WithExec([]string{"clojure", "-T:build", "jar"})

		jarFile := buildStage.File("target/my_app.jar")
		runtimeStage := dag.Container(opts).From("openjdk:20-slim").
			WithFile("/my_app.jar", jarFile).
			WithExposedPort(58950).
			WithEntrypoint([]string{"java", "-jar", "/my_app.jar"})
		return runtimeStage.Publish(ctx, publishTag)
	}

	// Create a background context for publishing images.
	ctx := context.Background()

	// Build and publish image for the default linux/amd64 platform.
	defaultOpts := dagger.ContainerOpts{Platform: dagger.Platform("linux/amd64")}
	publishedImage, err := buildAndPublish(defaultOpts, "ttl.sh/my-app--linux-amd64:2h", ctx)
	if err != nil {
		log.Fatal(err)
	}
	fmt.Printf("Successfully published image to local repository: %s\n", publishedImage)

	// Build and publish image for the host architecture.
	hostPlatformStr := fmt.Sprintf("%s/%s", runtime.GOOS, runtime.GOARCH)
	hostOpts := dagger.ContainerOpts{Platform: dagger.Platform(hostPlatformStr)}
	publishedImageHost, err := buildAndPublish(hostOpts, "ttl.sh/my-app--host:2h", ctx)
	if err != nil {
		log.Fatal(err)
	}
	fmt.Printf("Successfully published image for host architecture to local repository: %s\n", publishedImageHost)
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
