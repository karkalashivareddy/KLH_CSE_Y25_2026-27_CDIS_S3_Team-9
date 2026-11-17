terraform {
  required_version = ">= 1.11.0, < 2.0.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "= 6.71.0"
    }

    random = {
      source  = "hashicorp/random"
      version = "= 3.9.0"
    }
  }

  # Supply bucket, key, region and use_lockfile=true through backend config.
  # The backend bucket is provisioned separately by the state bootstrap process.
  backend "s3" {}
}

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project     = "CloudResilience"
      Environment = var.environment
      ManagedBy   = "Terraform"
      Owner       = var.owner_tag
    }
  }
}

provider "random" {}
