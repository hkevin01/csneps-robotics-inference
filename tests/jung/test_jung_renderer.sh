#!/bin/bash

# Test script for enhanced JUNG renderer
echo "Testing enhanced JUNG renderer..."

cd java/tools/jung-renderer

# Clean and compile
echo "Building JUNG renderer..."
mvn clean compile -q

if [ $? -ne 0 ]; then
    echo "❌ Compilation failed"
    exit 1
fi

echo "✅ Compilation successful"

# Test 1: Generate toy SVG
echo "Test 1: Generating toy SVG graph..."
mvn exec:java -Dexec.mainClass="com.csri.render.GraphSvg" \
    -Dexec.args="--focus TestNode --radius 2 --out-svg /tmp/toy-graph.svg" -q

if [ -f "/tmp/toy-graph.svg" ]; then
    echo "✅ SVG generation successful: /tmp/toy-graph.svg"
    echo "   Size: $(wc -c < /tmp/toy-graph.svg) bytes"
else
    echo "❌ SVG generation failed"
fi

# Test 2: Generate toy JSON layout
echo "Test 2: Generating toy JSON layout..."
mvn exec:java -Dexec.mainClass="com.csri.render.GraphSvg" \
    -Dexec.args="--focus TestNode --radius 2 --out-json /tmp/toy-layout.json" -q

if [ -f "/tmp/toy-layout.json" ]; then
    echo "✅ JSON layout generation successful: /tmp/toy-layout.json"
    echo "   Sample content:"
    head -10 /tmp/toy-layout.json | sed 's/^/   /'
else
    echo "❌ JSON layout generation failed"
fi

# Test 3: Test with KK layout
echo "Test 3: Testing KK layout algorithm..."
mvn exec:java -Dexec.mainClass="com.csri.render.GraphSvg" \
    -Dexec.args="--focus KKTest --radius 3 --layout kk --out-svg /tmp/kk-graph.svg --out-json /tmp/kk-layout.json" -q

if [ -f "/tmp/kk-graph.svg" ] && [ -f "/tmp/kk-layout.json" ]; then
    echo "✅ KK layout test successful"
else
    echo "❌ KK layout test failed"
fi

echo ""
echo "JUNG renderer tests completed!"
echo "Generated files:"
ls -la /tmp/toy-* /tmp/kk-* 2>/dev/null || echo "(none)"
