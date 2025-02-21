#!/bin/bash

set -e  # Exit on error

# Color codes for better visibility
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo_step() {
    echo -e "${BLUE}==> $1${NC}"
}

# Function to run local development environment
run_local() {
    echo_step "Starting local development environment..."
    echo_step "This will start XTDB and the Clojure web application"
    
    cd ci
    dagger call run-local-web-app --src-dir ../my-app up \
        --ports 58950:58950 \
        --ports 3000:3000 \
        --ports 5432:5432 \
        --ports 8080:8080
    
    echo_step "Local environment is running!"
    echo_step "Access points:"
    echo "  - Web App: http://localhost:58950"
    echo "  - XTDB HTTP API: http://localhost:3000"
    echo "  - XTDB PostgreSQL: localhost:5432"
    echo "  - XTDB Health: http://localhost:8080/healthz/alive"
}

# Function to run just the database environment
run_db() {
    echo_step "Starting database environment (XTDB)..."
    
    cd ci
    dagger call run-local-development up \
        --ports 3000:3000 \
        --ports 5432:5432 \
        --ports 8080:8080
    
    echo_step "Database environment is running!"
    echo_step "Access points:"
    echo "  - XTDB HTTP API: http://localhost:3000"
    echo "  - XTDB PostgreSQL: localhost:5432"
    echo "  - XTDB Health: http://localhost:8080/healthz/alive"
}

# Function to build and publish the web application
publish_app() {
    echo_step "Building and publishing Clojure web application..."
    
    cd ci
    dagger call build-and-publish-clj-web-app --src-dir ../my-app
    
    echo_step "Application has been built and published!"
    echo "Check the output above for the published image URLs"
}

# Help message
show_help() {
    echo "Usage: $0 [command]"
    echo
    echo "Commands:"
    echo "  local    - Run full local environment (XTDB + Web App)"
    echo "  db       - Run only database environment (XTDB)"
    echo "  publish  - Build and publish the web application"
    echo "  help     - Show this help message"
}

# Main script logic
case "$1" in
    "local")
        run_local
        ;;
    "db")
        run_db
        ;;
    "publish")
        publish_app
        ;;
    "help"|"")
        show_help
        ;;
    *)
        echo "Unknown command: $1"
        echo
        show_help
        exit 1
        ;;
esac 