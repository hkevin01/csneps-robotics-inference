#!/usr/bin/env python3
"""
CSNePS Robotics Inference - Demo Script v0.1.0

This script demonstrates the three canonical rule paths:
1. SLAM: LoopClosure → HighConfidenceLandmark
2. GNC: ThrusterAnomaly → Hypothesis
3. Medical: Finding → Recommendation

Run with: python demo_all.py
"""

import asyncio
import logging
import json
import sys
import time
from datetime import datetime
from typing import List, Dict, Any

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

# Import our CSNePS client
try:
    from csri_client import (
        CSNeRSClient, LoopClosure, AppearanceMatch, GNCEvent, MedicalFinding,
        BeliefQuery, JustificationQuery, send_observation, query_all_concepts
    )
except ImportError:
    logger.error("Failed to import csri_client. Make sure it's in the same directory.")
    sys.exit(1)

class CSNeRSDemoRunner:
    """
    Demonstrates the CSNePS Robotics Inference system v0.1.0

    This class runs through the three canonical rule paths and shows
    how observations trigger inference rules to generate new beliefs.
    """

    def __init__(self, host: str = "localhost", port: int = 50051):
        self.client = CSNeRSClient(host, port)
        self.demo_results = {
            "slam": [],
            "gnc": [],
            "medical": []
        }

    async def setup(self) -> bool:
        """Initialize the demo by connecting to CSNePS"""
        logger.info("=== CSNePS Robotics Inference Demo v0.1.0 ===")
        logger.info(f"Connecting to CSNePS server at {self.client.address}")

        if await self.client.connect():
            logger.info("✓ Connected to CSNePS server")
            return True
        else:
            logger.error("✗ Failed to connect to CSNePS server")
            logger.error("Make sure the Clojure server is running with: lein run")
            return False

    async def cleanup(self):
        """Clean up resources"""
        await self.client.disconnect()
        logger.info("Demo completed and disconnected")

    async def demo_slam_path(self):
        """
        Demonstrate SLAM rule path: LoopClosure → HighConfidenceLandmark

        This shows how loop closure detections and appearance matches
        can trigger rules to establish high-confidence landmarks.
        """
        logger.info("\n🗺️  === SLAM DOMAIN DEMO ===")
        logger.info("Demonstrating: LoopClosure → HighConfidenceLandmark")

        # Step 1: Send a high-confidence loop closure
        logger.info("\n📍 Step 1: Sending high-confidence loop closure observation")
        loop_closure = LoopClosure(
            landmark_id="L001",
            score=0.85,
            method="visual",
            pose_estimate=[10.5, 3.2, 0.0, 0.0, 0.0, 0.0, 1.0]
        )

        response = await self.client.send_loop_closure(loop_closure)
        self.demo_results["slam"].append({
            "step": 1,
            "observation": "LoopClosure",
            "response": response
        })

        if response.success:
            logger.info(f"✓ Loop closure processed: {response.observation_id}")
            logger.info(f"✓ Triggered rules: {', '.join(response.triggered_rules)}")
        else:
            logger.error(f"✗ Loop closure failed: {response.message}")

        await asyncio.sleep(0.5)  # Brief pause for clarity

        # Step 2: Send a consistent appearance match
        logger.info("\n👁️  Step 2: Sending consistent appearance match")
        appearance_match = AppearanceMatch(
            landmark_id="L001",
            consistency="consistent",
            score=0.78
        )

        response = await self.client.send_appearance_match(appearance_match)
        self.demo_results["slam"].append({
            "step": 2,
            "observation": "AppearanceMatch",
            "response": response
        })

        if response.success:
            logger.info(f"✓ Appearance match processed: {response.observation_id}")
            logger.info(f"✓ Triggered rules: {', '.join(response.triggered_rules)}")
        else:
            logger.error(f"✗ Appearance match failed: {response.message}")

        await asyncio.sleep(0.5)

        # Step 3: Query for high confidence landmarks
        logger.info("\n🔍 Step 3: Querying for high confidence landmarks")
        belief_query = BeliefQuery(
            concept="HighConfidenceLandmark",
            limit=5,
            include_justification=True
        )

        response = await self.client.query_beliefs(belief_query)
        self.demo_results["slam"].append({
            "step": 3,
            "query": "HighConfidenceLandmark",
            "response": response
        })

        if response.success:
            logger.info(f"✓ Found {len(response.beliefs)} high confidence landmarks")
            for belief in response.beliefs:
                content = json.loads(belief.content)
                logger.info(f"  • {belief.belief_type}: {content.get('landmark_id', 'unknown')} "
                           f"(confidence: {belief.confidence:.2f})")
        else:
            logger.error(f"✗ Landmark query failed: {response.message}")

    async def demo_gnc_path(self):
        """
        Demonstrate GNC rule path: ThrusterAnomaly → Hypothesis

        This shows how thruster anomaly events trigger rules to
        generate hypotheses about system failures.
        """
        logger.info("\n🚀 === GNC DOMAIN DEMO ===")
        logger.info("Demonstrating: ThrusterAnomaly → Hypothesis")

        # Step 1: Send a thruster anomaly during burn
        logger.info("\n⚠️  Step 1: Sending thruster anomaly during burn phase")
        gnc_event = GNCEvent(
            event_type="thruster_anomaly",
            mode="burn",
            severity=0.65,
            parameters={
                "thrust_deviation": 0.15,
                "temperature_spike": 25.0,
                "vibration_amplitude": 2.3
            }
        )

        response = await self.client.send_gnc_event(gnc_event)
        self.demo_results["gnc"].append({
            "step": 1,
            "observation": "GNCEvent",
            "response": response
        })

        if response.success:
            logger.info(f"✓ GNC event processed: {response.observation_id}")
            logger.info(f"✓ Triggered rules: {', '.join(response.triggered_rules)}")
        else:
            logger.error(f"✗ GNC event failed: {response.message}")

        await asyncio.sleep(0.5)

        # Step 2: Send a high-severity anomaly
        logger.info("\n🔥 Step 2: Sending high-severity thruster anomaly")
        critical_event = GNCEvent(
            event_type="thruster_anomaly",
            mode="burn",
            severity=0.85,
            parameters={
                "thrust_deviation": 0.45,
                "temperature_spike": 75.0,
                "vibration_amplitude": 8.1
            }
        )

        response = await self.client.send_gnc_event(critical_event)
        self.demo_results["gnc"].append({
            "step": 2,
            "observation": "GNCEvent",
            "response": response
        })

        if response.success:
            logger.info(f"✓ Critical GNC event processed: {response.observation_id}")
            logger.info(f"✓ Triggered rules: {', '.join(response.triggered_rules)}")
        else:
            logger.error(f"✗ Critical GNC event failed: {response.message}")

        await asyncio.sleep(0.5)

        # Step 3: Query for hypotheses
        logger.info("\n🔍 Step 3: Querying for generated hypotheses")
        belief_query = BeliefQuery(
            concept="Hypothesis",
            limit=5,
            include_justification=True
        )

        response = await self.client.query_beliefs(belief_query)
        self.demo_results["gnc"].append({
            "step": 3,
            "query": "Hypothesis",
            "response": response
        })

        if response.success:
            logger.info(f"✓ Found {len(response.beliefs)} hypotheses")
            for belief in response.beliefs:
                content = json.loads(belief.content)
                logger.info(f"  • {belief.belief_type}: {content.get('hypothesis', 'unknown')} "
                           f"(confidence: {belief.confidence:.2f})")
        else:
            logger.error(f"✗ Hypothesis query failed: {response.message}")

    async def demo_medical_path(self):
        """
        Demonstrate Medical rule path: Finding → Recommendation

        This shows how medical findings trigger rules to generate
        clinical recommendations.
        """
        logger.info("\n🏥 === MEDICAL DOMAIN DEMO ===")
        logger.info("Demonstrating: Finding → Recommendation")

        # Step 1: Send a large lesion finding
        logger.info("\n🔬 Step 1: Sending large lesion finding")
        medical_finding = MedicalFinding(
            patient_id="P001",
            finding_type="lesion",
            confidence=0.89,
            location="liver_segment_4",
            size_mm=12.5,
            modality="CT"
        )

        response = await self.client.send_medical_finding(medical_finding)
        self.demo_results["medical"].append({
            "step": 1,
            "observation": "MedicalFinding",
            "response": response
        })

        if response.success:
            logger.info(f"✓ Medical finding processed: {response.observation_id}")
            logger.info(f"✓ Triggered rules: {', '.join(response.triggered_rules)}")
        else:
            logger.error(f"✗ Medical finding failed: {response.message}")

        await asyncio.sleep(0.5)

        # Step 2: Send a small calcification
        logger.info("\n🔍 Step 2: Sending small calcification finding")
        small_finding = MedicalFinding(
            patient_id="P002",
            finding_type="calcification",
            confidence=0.76,
            location="lung_upper_lobe",
            size_mm=3.2,
            modality="CT"
        )

        response = await self.client.send_medical_finding(small_finding)
        self.demo_results["medical"].append({
            "step": 2,
            "observation": "MedicalFinding",
            "response": response
        })

        if response.success:
            logger.info(f"✓ Small finding processed: {response.observation_id}")
            logger.info(f"✓ Triggered rules: {', '.join(response.triggered_rules)}")
        else:
            logger.error(f"✗ Small finding failed: {response.message}")

        await asyncio.sleep(0.5)

        # Step 3: Query for recommendations
        logger.info("\n💊 Step 3: Querying for clinical recommendations")
        belief_query = BeliefQuery(
            concept="Recommendation",
            limit=5,
            include_justification=True
        )

        response = await self.client.query_beliefs(belief_query)
        self.demo_results["medical"].append({
            "step": 3,
            "query": "Recommendation",
            "response": response
        })

        if response.success:
            logger.info(f"✓ Found {len(response.beliefs)} recommendations")
            for belief in response.beliefs:
                content = json.loads(belief.content)
                logger.info(f"  • {belief.belief_type}: {content.get('action', 'unknown')} "
                           f"({content.get('priority', 'unknown')} priority, "
                           f"confidence: {belief.confidence:.2f})")
        else:
            logger.error(f"✗ Recommendation query failed: {response.message}")

    async def demo_justification(self):
        """
        Demonstrate justification queries for explainable AI
        """
        logger.info("\n🧠 === JUSTIFICATION DEMO ===")
        logger.info("Demonstrating explainable AI with justification queries")

        # Query all beliefs first
        all_beliefs = await query_all_concepts(self.client, limit=2)

        # Find a belief to get justification for
        target_belief = None
        for concept, response in all_beliefs.items():
            if response.success and response.beliefs:
                target_belief = response.beliefs[0]
                break

        if target_belief:
            logger.info(f"\n🔍 Getting justification for belief: {target_belief.belief_id}")

            justification_query = JustificationQuery(
                belief_id=target_belief.belief_id,
                max_depth=5
            )

            response = await self.client.get_justification(justification_query)

            if response.success:
                logger.info("✓ Justification retrieved:")
                justification = json.loads(response.justification_tree)
                logger.info(f"  Rule path: {' → '.join(justification.get('rule_path', []))}")
                logger.info(f"  Premises: {', '.join(justification.get('premises', []))}")
                logger.info(f"  Confidence: {justification.get('confidence', 0):.2f}")
            else:
                logger.error(f"✗ Justification failed: {response.message}")
        else:
            logger.warning("No beliefs found to demonstrate justification")

    async def print_summary(self):
        """Print a summary of the demo results"""
        logger.info("\n📊 === DEMO SUMMARY ===")

        total_observations = 0
        successful_observations = 0

        for domain, results in self.demo_results.items():
            observations = [r for r in results if "observation" in r]
            queries = [r for r in results if "query" in r]

            total_observations += len(observations)
            successful_observations += sum(1 for r in observations if r["response"].success)

            logger.info(f"\n{domain.upper()} Domain:")
            logger.info(f"  Observations sent: {len(observations)}")
            logger.info(f"  Queries executed: {len(queries)}")

            for result in observations:
                status = "✓" if result["response"].success else "✗"
                logger.info(f"    {status} {result['observation']}: {result['response'].message}")

        success_rate = (successful_observations / total_observations * 100) if total_observations > 0 else 0
        logger.info(f"\nOverall Success Rate: {success_rate:.1f}% ({successful_observations}/{total_observations})")

        if success_rate >= 100:
            logger.info("🎉 All demonstrations completed successfully!")
        elif success_rate >= 80:
            logger.info("✅ Most demonstrations completed successfully!")
        else:
            logger.warning("⚠️  Some demonstrations failed. Check the logs above.")

    async def run_full_demo(self):
        """Run the complete demonstration"""
        try:
            # Setup
            if not await self.setup():
                return False

            # Run each domain demo
            await self.demo_slam_path()
            await self.demo_gnc_path()
            await self.demo_medical_path()
            await self.demo_justification()

            # Print summary
            await self.print_summary()

            return True

        except KeyboardInterrupt:
            logger.info("\nDemo interrupted by user")
            return False
        except Exception as e:
            logger.error(f"Demo failed with error: {e}")
            return False
        finally:
            await self.cleanup()

async def main():
    """Main entry point for the demo script"""
    import argparse

    parser = argparse.ArgumentParser(description="CSNePS Robotics Inference Demo v0.1.0")
    parser.add_argument("--host", default="localhost", help="CSNePS server host")
    parser.add_argument("--port", type=int, default=50051, help="CSNePS server port")
    parser.add_argument("--domain", choices=["slam", "gnc", "medical", "all"],
                       default="all", help="Which domain to demo")

    args = parser.parse_args()

    demo = CSNeRSDemoRunner(args.host, args.port)

    if args.domain == "all":
        success = await demo.run_full_demo()
    else:
        # Run individual domain demo
        if not await demo.setup():
            return 1

        try:
            if args.domain == "slam":
                await demo.demo_slam_path()
            elif args.domain == "gnc":
                await demo.demo_gnc_path()
            elif args.domain == "medical":
                await demo.demo_medical_path()

            await demo.print_summary()
            success = True

        except Exception as e:
            logger.error(f"Domain demo failed: {e}")
            success = False
        finally:
            await demo.cleanup()

    return 0 if success else 1

if __name__ == "__main__":
    sys.exit(asyncio.run(main()))
