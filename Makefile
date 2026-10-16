.PHONY: demo demo-reset dev-db dev-backend dev-frontend test images api-types

# The whole app from source, Docker only (see README, "Quick demo").
demo:
	cd demo && docker compose up --build

# Stops the demo and deletes its data.
demo-reset:
	cd demo && docker compose down -v

dev-db:
	docker compose up -d

dev-backend:
	cd backend && ./mvnw spring-boot:run

dev-frontend:
	cd frontend && npm run dev

# Regenerate the frontend's API types; the backend must be running.
api-types:
	cd frontend && npm run api:types

test:
	cd backend && ./mvnw verify
	cd frontend && npm run lint && npm run typecheck && npm test

images:
	cd backend && ./mvnw spring-boot:build-image
	docker build -t not-at-home-frontend frontend
