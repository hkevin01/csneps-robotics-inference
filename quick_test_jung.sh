#!/bin/bash

# Quick test of JUNG renderer functionality
echo "🎯 Quick JUNG Renderer Test"
echo "=========================="

cd /home/kevin/Projects/csneps-robotics-inference/java/tools/jung-renderer

echo "Building JUNG renderer..."
mvn clean compile -q

if [ $? -ne 0 ]; then
    echo "❌ Compilation failed - checking errors..."
    mvn compile
    exit 1
fi

echo "✅ Compilation successful"

echo ""
echo "Testing basic functionality..."

# Test 1: Generate toy graph
echo "Test 1: Basic toy graph generation..."
mvn exec:java -Dexec.mainClass="com.csri.render.GraphSvg" \
    -Dexec.args="--focus TestNode --radius 2 --out-svg /tmp/test1.svg --out-json /tmp/test1.json" -q

if [ -f "/tmp/test1.svg" ] && [ -f "/tmp/test1.json" ]; then
    echo "✅ Test 1 passed"
    echo "   SVG: $(wc -c < /tmp/test1.svg) bytes"
    echo "   JSON: $(wc -c < /tmp/test1.json) bytes"

    echo "Sample SVG content:"
    head -5 /tmp/test1.svg | sed 's/^/   /'

    echo "Sample JSON content:"
    head -10 /tmp/test1.json | sed 's/^/   /'
else
    echo "❌ Test 1 failed"
fi

echo ""
echo "Test 2: KK layout algorithm..."
mvn exec:java -Dexec.mainClass="com.csri.render.GraphSvg" \
    -Dexec.args="--focus KKTest --radius 1 --layout kk --out-svg /tmp/test2.svg" -q

if [ -f "/tmp/test2.svg" ]; then
    echo "✅ Test 2 passed - KK layout working"
else
    echo "❌ Test 2 failed"
fi

echo ""
echo "🏁 JUNG Renderer Quick Test Complete"
