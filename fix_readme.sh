#!/bin/bash
# Backup current README.md
cp /home/kevin/Projects/csneps-robotics-inference/README.md /home/kevin/Projects/csneps-robotics-inference/README_BACKUP_$(date +%Y%m%d_%H%M%S).md

# Replace with clean version
cp /home/kevin/Projects/csneps-robotics-inference/README_FINAL_FORMATTED.md /home/kevin/Projects/csneps-robotics-inference/README.md

echo "README.md has been replaced with clean version"
