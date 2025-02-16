package main

import (
	"github.com/aws/aws-cdk-go/awscdk/v2"
	"github.com/aws/aws-cdk-go/awscdk/v2/awsecs"
	"github.com/aws/aws-cdk-go/awscdk/v2/awsefs"
	"github.com/aws/aws-cdk-go/awscdk/v2/awsecrassets"
    "github.com/aws/aws-cdk-go/awscdk/v2/awsecr" // Import ECR
	"github.com/aws/constructs-go/constructs/v10"
	"github.com/aws/jsii-runtime-go"
	"github.com/hashicorp/terraform-cdk-go/cdktf"
)

func NewMyStack(scope constructs.Construct, id string) cdktf.TerraformStack {
	stack := cdktf.NewTerraformStack(scope, &id)

	// Configure the AWS Provider
	cdktf.NewTerraformAwsProvider(stack, jsii.String("AWS"), &cdktf.TerraformAwsProviderConfig{
		Region: jsii.String("us-east-1"), // Or your preferred region
	})

	// Create an ECR Asset for the XTDB image (assuming you'll push it to ECR)
	xtdbImage := awsecrassets.NewDockerImageAsset(stack, jsii.String("XTDBImage"), &awsecrassets.DockerImageAssetProps{
		Directory: jsii.String("../my-app"), // Assuming Dockerfile is in my-app
		AssetName: jsii.String("xtdb-image"),
	})

    // Create an ECR Repository for the Clojure App image
    appRepo := awsecr.NewRepository(stack, jsii.String("AppRepo"), &awsecr.RepositoryProps{
        RepositoryName: jsii.String("clj-xtdb-devops-app"), // Choose a suitable name
    })


    // Create an ECR Asset for the Clojure App image
	appImage := awsecrassets.NewDockerImageAsset(stack, jsii.String("AppImage"), &awsecrassets.DockerImageAssetProps{
		Directory: jsii.String("../my-app"),
		AssetName: jsii.String("app-image"),
        Repository: appRepo, // Associate with the ECR repository
	})


	// Create an ECS Cluster
	cluster := awsecs.NewCluster(stack, jsii.String("XTDBCluster"), &awsecs.ClusterProps{})

	// Create an EFS filesystem for persistent XTDB data
	fs := awsefs.NewFileSystem(stack, jsii.String("XTDBFileSystem"), &awsefs.FileSystemProps{
		Vpc: cluster.Vpc(),
	})

	// Create a Task Definition for XTDB
	taskDef := awsecs.NewFargateTaskDefinition(stack, jsii.String("XTDBTaskDef"), &awsecs.FargateTaskDefinitionProps{
		MemoryLimitMiB: jsii.Number(1024),
		Cpu:            jsii.Number(512),
		Volumes: &[]*awsecs.Volume{
			{
				Name: jsii.String("xtdb-data"),
				EfsVolumeConfiguration: &awsecs.EfsVolumeConfiguration{
					FileSystemId: fs.FileSystemId(),
				},
			},
		},
	})

	taskDef.AddContainer(jsii.String("XTDBContainer"), &awsecs.ContainerDefinitionOptions{
		Image: awsecs.ContainerImage_FromEcrRepository(xtdbImage.Repository(), xtdbImage.ImageTag()),
		PortMappings: &[]*awsecs.PortMapping{
			{
				ContainerPort: jsii.Number(3000),
				HostPort:      jsii.Number(3000),
			},
		},
		Logging: awsecs.LogDrivers_AwsLogs(&awsecs.AwsLogDriverProps{
			StreamPrefix: jsii.String("xtdb"),
		}),
	})

	taskDef.FindContainer(jsii.String("XTDBContainer")).AddMountPoints(&awsecs.MountPoint{
		ContainerPath: jsii.String("/var/lib/xtdb"),
		SourceVolume:  jsii.String("xtdb-data"),
		ReadOnly:      jsii.Bool(false),
	})

	// Create a Service for XTDB
	awsecs.NewFargateService(stack, jsii.String("XTDBService"), &awsecs.FargateServiceProps{
		Cluster:        cluster,
		TaskDefinition: taskDef,
		DesiredCount:   jsii.Number(1),
	})

    // Create a Task Definition for the Clojure App
    appTaskDef := awsecs.NewFargateTaskDefinition(stack, jsii.String("AppTaskDef"), &awsecs.FargateTaskDefinitionProps{
        MemoryLimitMiB: jsii.Number(512),
        Cpu:            jsii.Number(256), // Adjust as needed
    })

    appTaskDef.AddContainer(jsii.String("AppContainer"), &awsecs.ContainerDefinitionOptions{
        Image: awsecs.ContainerImage_FromEcrRepository(appRepo, jsii.String("latest")), // Use the ECR repo and tag
        PortMappings: &[]*awsecs.PortMapping{
            {
                ContainerPort: jsii.Number(58950),
                HostPort:      jsii.Number(58950),
            },
        },
        Environment: &map[string]*string{
            "XTDB_ADDR": jsii.String("xtdb-service.local:3000"), // Assuming service discovery is set up.  This needs to be resolvable.
			"APP_ENV":   jsii.String("production"),
        },
		Logging: awsecs.LogDrivers_AwsLogs(&awsecs.AwsLogDriverProps{ // Add logging
			StreamPrefix: jsii.String("clj-app"),
		}),
    })


    // Create a Service for the Clojure App
    awsecs.NewFargateService(stack, jsii.String("AppService"), &awsecs.FargateServiceProps{
        Cluster:        cluster,
        TaskDefinition: appTaskDef,
        DesiredCount:   jsii.Number(1),
    })
	return stack
}

func main() {
	app := cdktf.NewApp(nil)

	NewMyStack(app, "infra")

	app.Synth()
}