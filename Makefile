.PHONY: dev-db dev-backend test images

dev-db:
	docker compose up -d

dev-backend:
	cd backend && SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run

test:
	cd backend && ./mvnw verify

images:
	cd backend && ./mvnw spring-boot:build-image
