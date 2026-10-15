.PHONY: dev-db dev-backend test images

dev-db:
	docker compose up -d

dev-backend:
	cd backend && ./mvnw spring-boot:run

test:
	cd backend && ./mvnw verify

images:
	cd backend && ./mvnw spring-boot:build-image
