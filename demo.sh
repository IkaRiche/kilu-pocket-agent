#!/usr/bin/env bash
set -e

echo "====================================================="
echo "  KiLu Pocket Agent - Quickstart Demo (Simulator)    "
echo "====================================================="
echo ""
echo "This script simulates the deployment of the Cloud Control Plane"
echo "and triggers a test extraction task against the network."
echo ""

# Check for node/npm
if ! command -v npm &> /dev/null; then
    echo "ERROR: npm could not be found. Please install Node.js."
    exit 1
fi

echo "[1/4] Installing Control Plane dependencies..."
cd cloud
npm install --silent

echo "[2/4] Starting local Control Plane (Wrangler dev)..."
echo "Starting background worker on http://127.0.0.1:8787"
# Run wrangler dev in the background and pipe output to a log
npx wrangler dev --port 8787 > ../demo_worker.log 2>&1 &
WORKER_PID=$!

# Wait for worker to boot
sleep 3
if ! kill -0 $WORKER_PID 2>/dev/null; then
    echo "ERROR: Worker failed to start. Check demo_worker.log"
    cat ../demo_worker.log
    exit 1
fi

echo "[3/4] Creating a mock task via API..."
# Create a task. This simulates the Hub pulling a task from the queue.
curl -s -X POST http://127.0.0.1:8787/v1/tasks \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer sim_token" \
  -d '{
    "title": "Demo Read-Only Extraction",
    "user_prompt": "Extract the main headline from example.com",
    "executor_preference": "HUB_PREFERRED"
  }' > /dev/null

echo ""
echo "✅ Task successfully submitted to the local Control Plane queue!"
echo ""
echo "Next steps:"
echo " 1. Open the KiLu Pocket Agent app on your Hub device."
echo " 2. Ensure your Hub's Control Plane URL is set to http://<your_local_ip>:8787"
echo " 3. The Hub will pick up the task and execute it or request Approver consent."
echo ""
echo "Press Ctrl+C to stop the local Control Plane simulator."

# Wait for user interrupt
wait $WORKER_PID

