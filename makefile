APP_NAME := auth-service
OUTPUT_DIR := /root/apps

.PHONY: build deploy clean

build:
	chmod +x ./mvnw
	./mvnw clean package -DskipTests

deploy: build
	mkdir -p $(OUTPUT_DIR)
	cp target/$(APP_NAME)-*.jar $(OUTPUT_DIR)/auth/$(APP_NAME).jar

clean:
	./mvnw clean
	rm -f $(OUTPUT_DIR)/$(APP_NAME).jar
