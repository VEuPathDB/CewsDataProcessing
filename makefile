GEN_PACKAGE  := $(shell ./gradlew -q print-gen-package)
APP_PACKAGE  := $(shell ./gradlew -q print-package)
PWD          := $(shell pwd)
MAIN_DIR     := src/main/java/$(shell echo $(APP_PACKAGE) | sed 's/\./\//g')
TEST_DIR     := $(shell echo $(MAIN_DIR) | sed 's/main/test/')
ALL_PACKABLE := $(shell find src/main -type f)
BIN_DIR      := .tools/bin

FETCH_EDA_COMMON_SCHEMA := $(shell ./gradlew -q "print-eda-common-schema-fetch")

C_BLUE := "\\033[94m"
C_NONE := "\\033[0m"
C_CYAN := "\\033[36m"

.PHONY: default
default:
	@echo "Please choose one of:"
	@echo ""
	@echo "$(C_BLUE)  make compile$(C_NONE)"
	@echo "    Compiles the existing code in 'src/'.  Regenerates files if the"
	@echo "    api spec has changed."
	@echo ""
	@echo "$(C_BLUE)  make test$(C_NONE)"
	@echo "    Compiles the existing code in 'src/' and runs unit tests."
	@echo "    Regenerates files if the api spec has changed."
	@echo ""
	@echo "$(C_BLUE)  make jar$(C_NONE)"
	@echo "    Compiles a 'fat jar' from this project and it's dependencies."
	@echo ""
	@echo "$(C_BLUE)  make docker$(C_NONE)"
	@echo "    Builds a runnable docker image for this service"
	@echo ""
	@echo "$(C_BLUE)  make install-dev-env$(C_NONE)"
	@echo "    Ensures the current dev environment has the necessary "
	@echo "    installable tools to build this project."
	@echo ""
	@echo "$(C_BLUE)  make gen-jaxrs$(C_NONE)"
	@echo "    Ensures the current dev environment has the necessary "
	@echo "    installable tools to build this project."
	@echo ""
	@echo "$(C_BLUE)  make clean$(C_NONE)"
	@echo "    Remove files generated by other targets; put project back in its"
	@echo "    original state."
	@echo ""

.PHONY: compile
compile: install-dev-env gen-jaxrs gen-docs
	@./gradlew clean compileJava

.PHONY: test
test: install-dev-env gen-jaxrs gen-docs
	@./gradlew clean test

.PHONY: jar
jar: install-dev-env build/libs/service.jar

.PHONY: docker
docker:
	@docker build --no-cache -t $(shell ./gradlew -q print-container-name) \
		--build-arg=GITHUB_USERNAME=$(GITHUB_USERNAME) \
		--build-arg=GITHUB_TOKEN=$(GITHUB_TOKEN) .

.PHONY: install-dev-env
install-dev-env:
	@if [ ! -d .tools ]; then \
		git clone https://github.com/VEuPathDB/lib-jaxrs-container-build-utils .tools; \
	else \
		cd .tools && git pull && cd ..; \
	fi
	cd .tools && git checkout jersey-3 && cd ..
	@$(BIN_DIR)/check-env.sh
	@$(BIN_DIR)/install-fgputil.sh
	@$(BIN_DIR)/install-oracle.sh
	@$(BIN_DIR)/install-raml2jaxrs.sh
	@$(BIN_DIR)/install-raml-merge.sh
	@$(BIN_DIR)/install-npm.sh

clean:
	@rm -rf .gradle .tools vendor build

fix-path:
	@$(BIN_DIR)/fix-path.sh $(MAIN_DIR)
	@$(BIN_DIR)/fix-path.sh $(TEST_DIR)

gen-jaxrs: api.raml merge-raml
	@$(BIN_DIR)/generate-jaxrs.sh $(GEN_PACKAGE)
	@$(BIN_DIR)/generate-jaxrs-streams.sh $(GEN_PACKAGE)
	@$(BIN_DIR)/generate-jaxrs-postgen-mods.sh $(GEN_PACKAGE)
	@grep -Rl javax src | xargs -I{} sed -i 's/javax.ws/jakarta.ws/g' {}

gen-docs: api.raml merge-raml
	@$(BIN_DIR)/generate-docs.sh

merge-raml:
	@echo "Downloading dependencies..."
	$(FETCH_EDA_COMMON_SCHEMA) > schema/url/eda-common-lib.raml
	$(BIN_DIR)/merge-raml schema > schema/library.raml
	rm schema/url/eda-common-lib.raml

#
# File based targets
#

build/libs/service.jar: \
      gen-jaxrs \
      gen-docs \
      vendor/fgputil-accountdb-1.0.0.jar \
      vendor/fgputil-cache-1.0.0.jar \
      vendor/fgputil-cli-1.0.0.jar \
      vendor/fgputil-core-1.0.0.jar \
      vendor/fgputil-db-1.0.0.jar \
      vendor/fgputil-events-1.0.0.jar \
      vendor/fgputil-json-1.0.0.jar \
      vendor/fgputil-server-1.0.0.jar \
      vendor/fgputil-servlet-1.0.0.jar \
      vendor/fgputil-solr-1.0.0.jar \
      vendor/fgputil-test-1.0.0.jar \
      vendor/fgputil-web-1.0.0.jar \
      vendor/fgputil-xml-1.0.0.jar \
      build.gradle.kts \
      service.properties
	@echo "$(C_BLUE)Building application jar$(C_NONE)"
	@./gradlew clean test jar
