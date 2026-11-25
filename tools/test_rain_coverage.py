#!/usr/bin/env python3
"""Run the actual platform-independent Java viewport adapter."""
from pathlib import Path
import subprocess
import tempfile
import unittest


class RainCoverageTest(unittest.TestCase):
    def test_actual_java_geometry(self):
        root=Path(__file__).resolve().parents[1]
        java=root/'.toolchain/jdk17/bin'
        with tempfile.TemporaryDirectory() as output:
            subprocess.run([java/'javac','-d',output,
                            root/'launcher/src/main/java/dev/sense/launcher/RainCoverage.java',
                            root/'tools/RainCoverageTest.java'],check=True)
            subprocess.run([java/'java','-cp',output,'dev.sense.launcher.RainCoverageTest'],check=True)


if __name__=='__main__':unittest.main()
