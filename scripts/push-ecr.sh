#!/usr/bin/env bash
# Build amd64 image and push to ECR (repo: kpay/kchat-api).
#
# Usage:
#   ./scripts/push-ecr.sh                  # tag = git short SHA + :latest
#   ./scripts/push-ecr.sh abc1234
#
# Pushes:
#   {registry}/kpay/kchat-api:<tag>
#   {registry}/kpay/kchat-api:latest   (when tag ≠ latest)

set -euo pipefail

default_tag() {
  if git rev-parse --short HEAD >/dev/null 2>&1; then
    git rev-parse --short HEAD
  else
    date +%Y%m%d%H%M%S
  fi
}

resolve_account_id() {
  if [[ -n "${AWS_ACCOUNT_ID:-}" ]]; then
    printf '%s' "$AWS_ACCOUNT_ID"
    return 0
  fi
  local id
  id="$(aws sts get-caller-identity --query Account --output text 2>/dev/null || true)"
  if [[ -z "$id" || "$id" == "None" ]]; then
    echo "ERROR: set AWS_ACCOUNT_ID or configure AWS CLI (aws sts get-caller-identity)" >&2
    return 1
  fi
  echo "==> AWS_ACCOUNT_ID from STS: ${id}" >&2
  printf '%s' "$id"
}

TAG="${1:-$(default_tag)}"
REGION="${AWS_REGION:-ap-southeast-1}"
ACCOUNT_ID="$(resolve_account_id)"

REGISTRY="${ACCOUNT_ID}.dkr.ecr.${REGION}.amazonaws.com"
IMAGE_LOCAL="kpay/kchat-api"
IMAGE_REMOTE="${REGISTRY}/kpay/kchat-api"
REPO_NAME="kpay/kchat-api"

cd "$(dirname "$0")/.."

if ! command -v docker >/dev/null 2>&1; then
  echo "ERROR: docker not found" >&2
  exit 1
fi

echo "==> Building ${IMAGE_LOCAL}:${TAG}"
echo "    registry=${REGISTRY}"
echo "    region=${REGION}"

docker build \
  --platform=linux/amd64 \
  -t "${IMAGE_LOCAL}:${TAG}" \
  -t "${IMAGE_LOCAL}:latest" \
  .

echo "==> Login ECR ${REGISTRY}"
aws ecr get-login-password --region "${REGION}" \
  | docker login --username AWS --password-stdin "${REGISTRY}"

echo "==> Tag for ECR"
docker tag "${IMAGE_LOCAL}:${TAG}" "${IMAGE_REMOTE}:${TAG}"
docker tag "${IMAGE_LOCAL}:${TAG}" "${IMAGE_REMOTE}:latest"

echo "==> Push ${IMAGE_REMOTE}:${TAG}"
docker push "${IMAGE_REMOTE}:${TAG}"

if [[ "${TAG}" != "latest" ]]; then
  echo "==> Push ${IMAGE_REMOTE}:latest"
  docker push "${IMAGE_REMOTE}:latest"
else
  echo "==> TAG is 'latest' — skipped duplicate push (pass a version/SHA as \$1 to get two tags)"
fi

echo "==> Verify tags on ECR"
aws ecr describe-images \
  --region "${REGION}" \
  --repository-name "${REPO_NAME}" \
  --image-ids imageTag="${TAG}" \
  --query 'imageDetails[0].{tags:imageTags,pushed:imagePushedAt,digest:imageDigest}' \
  --output table

echo "==> Done"
echo "    ${IMAGE_REMOTE}:${TAG}"
if [[ "${TAG}" != "latest" ]]; then
  echo "    ${IMAGE_REMOTE}:latest"
fi
