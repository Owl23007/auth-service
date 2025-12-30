#!/bin/bash

# Auth Service Control Script
# Usage: ./run.sh {start|stop|restart|status}

set -e

# ========== 固定部署路径 ==========
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
JAR_FILE="auth-service.jar"
PID_FILE="$SCRIPT_DIR/app.pid"
LOG_DIR="$SCRIPT_DIR/logs"
LOG_FILE="$LOG_DIR/app.log"
ENV_FILE="$SCRIPT_DIR/.env"

JVM_OPTS="-Xms512m -Xmx1024m -Dfile.encoding=UTF-8 -Duser.timezone=Asia/Shanghai -Djava.io.tmpdir=$SCRIPT_DIR/tmp"

print_error() {
    echo "[ERROR]	$1" >&2
}

print_info() {
    echo "[INFO]	$1"
}

print_success() {
    echo "[OK]	$1"
}

print_warning() {
    echo "[WARN]	$1"
}

# === 加载 .env ===
load_env() {
    if [[ -f "$ENV_FILE" ]]; then
        while IFS='=' read -r key value; do
            [[ -z "$key" ]] && continue
            [[ "$key" =~ ^[[:space:]]*# ]] && continue
            key=$(echo "$key" | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')
            [[ "$key" =~ ^[a-zA-Z_][a-zA-Z0-9_]*$ ]] || continue
            value=$(echo "$value" | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')
            export "$key=$value"
        done < "$ENV_FILE"
    fi
}

# === 检查服务是否运行 ===
is_running() {
    if [[ -f "$PID_FILE" ]]; then
        PID=$(cat "$PID_FILE")
        if kill -0 "$PID" 2>/dev/null; then
            return 0
        else
            rm -f "$PID_FILE"
        fi
    fi
    return 1
}

# === 启动服务 ===
start() {
    if is_running; then
        print_success "Service already running, PID: $(cat $PID_FILE)"
        exit 0
    fi

    load_env
    [[ -f "$SCRIPT_DIR/$JAR_FILE" ]] || {
        print_error "JAR file not found: $JAR_FILE"
        exit 1
    }

    mkdir -p "$LOG_DIR"
    mkdir -p "$SCRIPT_DIR/tmp"

    print_info "Starting service: $JAR_FILE"
    print_info "Log file: $LOG_FILE"
    print_info "Working dir: $SCRIPT_DIR"
    if [[ -n "${SERVER_PORT:-}" ]]; then
        print_info "Server port: $SERVER_PORT"
    fi

    # 使用 nohup（兼容手动运行），重定向日志
    nohup java $JVM_OPTS -jar "$SCRIPT_DIR/$JAR_FILE" > "$LOG_FILE" 2>&1 &
    echo $! > "$PID_FILE"
    print_success "Service started, PID: $(cat $PID_FILE)"
}

# === 停止服务 ===
stop() {
    if ! is_running; then
        print_warning "Service is not running"
        return 0
    fi

    PID=$(cat "$PID_FILE")
    print_info "Stopping service (PID: $PID)..."
    kill "$PID"
    sleep 2

    if is_running; then
        print_warning "Graceful shutdown failed, forcing kill..."
        kill -9 "$PID" 2>/dev/null || true
    fi

    rm -f "$PID_FILE"
    print_success "Service stopped"
}

# === 主逻辑 ===
case "${1:-start}" in
    start)
        start
        ;;
    stop)
        stop
        ;;
    restart)
        stop
        sleep 1
        start
        ;;
    status)
        if is_running; then
            print_success "Running (PID: $(cat $PID_FILE))"
            print_info "Log: tail -f $LOG_FILE"
        else
            print_warning "Stopped"
        fi
        ;;
    *)
        echo "Usage: $0 {start|stop|restart|status}"
        exit 1
        ;;
esac
