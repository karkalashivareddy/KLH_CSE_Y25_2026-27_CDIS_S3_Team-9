resource "aws_ecr_repository" "api" {
  name                 = "${local.name}/api"
  image_tag_mutability = "IMMUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }

  encryption_configuration {
    encryption_type = "KMS"
    kms_key         = aws_kms_key.application.arn
  }

  tags = merge(local.common_tags, { Name = "${local.name}-api" })
}

resource "aws_ecr_repository" "web" {
  name                 = "${local.name}/web"
  image_tag_mutability = "IMMUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }

  encryption_configuration {
    encryption_type = "KMS"
    kms_key         = aws_kms_key.application.arn
  }

  tags = merge(local.common_tags, { Name = "${local.name}-web" })
}

resource "aws_ecr_lifecycle_policy" "api" {
  repository = aws_ecr_repository.api.name
  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Keep 30 newest immutable build tags."
      selection = {
        tagStatus      = "tagged"
        tagPatternList = ["*"]
        countType      = "imageCountMoreThan"
        countNumber    = 30
      }
      action = { type = "expire" }
    }]
  })
}

resource "aws_ecr_lifecycle_policy" "web" {
  repository = aws_ecr_repository.web.name
  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Keep 30 newest immutable build tags."
      selection = {
        tagStatus      = "tagged"
        tagPatternList = ["*"]
        countType      = "imageCountMoreThan"
        countNumber    = 30
      }
      action = { type = "expire" }
    }]
  })
}
