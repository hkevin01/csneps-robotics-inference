#!/bin/bash

# Backup current README.md
cp README.md README_BACKUP_$(date +%Y%m%d_%H%M%S).md

# Copy clean version to replace README.md
cp README_NEW_CLEAN.md README.md

echo "README.md has been replaced with clean version"
echo "File size check:"
wc -l README.md
echo "Markdown error check:"
echo "Should show 'No errors found' for clean file"
