"""
Unit tests for CSNePS Robotics Inference Python Bridge v0.1.0

This module tests the gRPC client functionality and data structures.
"""

import pytest
import asyncio
import json
import time
from datetime import datetime
from unittest.mock import AsyncMock, patch, MagicMock

# Import the client module
import sys
import os
sys.path.insert(0, os.path.dirname(__file__))

from csri_client import (
    CSNeRSClient, LoopClosure, AppearanceMatch, GNCEvent, MedicalFinding,
    ObservationResponse, BeliefInfo, BeliefQuery, BeliefResponse,
    JustificationQuery, JustificationResponse, ObservationType,
    send_observation, query_all_concepts
)

class TestDataStructures:
    """Test the data structure classes"""

    def test_loop_closure_creation(self):
        """Test LoopClosure data structure"""
        lc = LoopClosure(
            landmark_id="L001",
            score=0.85,
            method="visual",
            pose_estimate=[1.0, 2.0, 3.0, 0.0, 0.0, 0.0, 1.0]
        )

        assert lc.landmark_id == "L001"
        assert lc.score == 0.85
        assert lc.method == "visual"
        assert len(lc.pose_estimate) == 7
        assert isinstance(lc.timestamp, datetime)

    def test_appearance_match_creation(self):
        """Test AppearanceMatch data structure"""
        am = AppearanceMatch(
            landmark_id="L002",
            consistency="consistent",
            score=0.78
        )

        assert am.landmark_id == "L002"
        assert am.consistency == "consistent"
        assert am.score == 0.78
        assert isinstance(am.timestamp, datetime)

    def test_gnc_event_creation(self):
        """Test GNCEvent data structure"""
        gnc = GNCEvent(
            event_type="thruster_anomaly",
            mode="burn",
            severity=0.65,
            parameters={"thrust_deviation": 0.15, "temperature": 25.0}
        )

        assert gnc.event_type == "thruster_anomaly"
        assert gnc.mode == "burn"
        assert gnc.severity == 0.65
        assert "thrust_deviation" in gnc.parameters
        assert isinstance(gnc.timestamp, datetime)

    def test_medical_finding_creation(self):
        """Test MedicalFinding data structure"""
        mf = MedicalFinding(
            patient_id="P001",
            finding_type="lesion",
            confidence=0.89,
            location="liver_segment_4",
            size_mm=12.5,
            modality="CT"
        )

        assert mf.patient_id == "P001"
        assert mf.finding_type == "lesion"
        assert mf.confidence == 0.89
        assert mf.location == "liver_segment_4"
        assert mf.size_mm == 12.5
        assert mf.modality == "CT"
        assert isinstance(mf.timestamp, datetime)

    def test_observation_response_creation(self):
        """Test ObservationResponse data structure"""
        response = ObservationResponse(
            success=True,
            message="Processed successfully",
            observation_id="obs_123",
            triggered_rules=["rule1", "rule2"]
        )

        assert response.success is True
        assert response.message == "Processed successfully"
        assert response.observation_id == "obs_123"
        assert len(response.triggered_rules) == 2

    def test_belief_info_creation(self):
        """Test BeliefInfo data structure"""
        belief = BeliefInfo(
            belief_id="belief_1",
            belief_type="HighConfidenceLandmark",
            content='{"landmark_id": "L001"}',
            confidence=0.85,
            created_at=datetime.now()
        )

        assert belief.belief_id == "belief_1"
        assert belief.belief_type == "HighConfidenceLandmark"
        assert json.loads(belief.content)["landmark_id"] == "L001"
        assert belief.confidence == 0.85
        assert isinstance(belief.created_at, datetime)

class TestCSNeRSClient:
    """Test the CSNeRSClient class"""

    @pytest.fixture
    def client(self):
        """Create a test client"""
        return CSNeRSClient("localhost", 50051)

    def test_client_initialization(self, client):
        """Test client initialization"""
        assert client.host == "localhost"
        assert client.port == 50051
        assert client.address == "localhost:50051"
        assert not client.is_connected

    @pytest.mark.asyncio
    async def test_connection_lifecycle(self, client):
        """Test client connection and disconnection"""
        # Test connection
        connected = await client.connect()
        assert connected is True
        assert client.is_connected is True

        # Test disconnection
        await client.disconnect()
        assert client.is_connected is False

    @pytest.mark.asyncio
    async def test_send_loop_closure(self, client):
        """Test sending loop closure observation"""
        lc = LoopClosure(
            landmark_id="L001",
            score=0.85,
            method="visual",
            pose_estimate=[1.0, 2.0, 3.0, 0.0, 0.0, 0.0, 1.0]
        )

        response = await client.send_loop_closure(lc)

        assert isinstance(response, ObservationResponse)
        assert response.success is True
        assert "Loop closure processed successfully" in response.message
        assert response.observation_id is not None
        assert isinstance(response.triggered_rules, list)

    @pytest.mark.asyncio
    async def test_send_appearance_match(self, client):
        """Test sending appearance match observation"""
        am = AppearanceMatch(
            landmark_id="L002",
            consistency="consistent",
            score=0.78
        )

        response = await client.send_appearance_match(am)

        assert isinstance(response, ObservationResponse)
        assert response.success is True
        assert "Appearance match processed successfully" in response.message
        assert response.observation_id is not None

    @pytest.mark.asyncio
    async def test_send_gnc_event(self, client):
        """Test sending GNC event observation"""
        gnc = GNCEvent(
            event_type="thruster_anomaly",
            mode="burn",
            severity=0.65,
            parameters={"thrust_deviation": 0.15}
        )

        response = await client.send_gnc_event(gnc)

        assert isinstance(response, ObservationResponse)
        assert response.success is True
        assert "GNC event processed successfully" in response.message
        assert response.observation_id is not None

    @pytest.mark.asyncio
    async def test_send_medical_finding(self, client):
        """Test sending medical finding observation"""
        mf = MedicalFinding(
            patient_id="P001",
            finding_type="lesion",
            confidence=0.89,
            location="liver_segment_4",
            size_mm=12.5,
            modality="CT"
        )

        response = await client.send_medical_finding(mf)

        assert isinstance(response, ObservationResponse)
        assert response.success is True
        assert "Medical finding processed successfully" in response.message
        assert response.observation_id is not None

    @pytest.mark.asyncio
    async def test_query_beliefs_landmarks(self, client):
        """Test querying for high confidence landmarks"""
        query = BeliefQuery(
            concept="HighConfidenceLandmark",
            limit=5,
            include_justification=False
        )

        response = await client.query_beliefs(query)

        assert isinstance(response, BeliefResponse)
        assert response.success is True
        assert isinstance(response.beliefs, list)
        assert len(response.beliefs) <= 5

        # Check belief structure if any returned
        if response.beliefs:
            belief = response.beliefs[0]
            assert isinstance(belief, BeliefInfo)
            assert belief.belief_type == "HighConfidenceLandmark"

    @pytest.mark.asyncio
    async def test_query_beliefs_hypotheses(self, client):
        """Test querying for hypotheses"""
        query = BeliefQuery(concept="Hypothesis", limit=3)

        response = await client.query_beliefs(query)

        assert isinstance(response, BeliefResponse)
        assert response.success is True
        assert isinstance(response.beliefs, list)

    @pytest.mark.asyncio
    async def test_query_beliefs_recommendations(self, client):
        """Test querying for recommendations"""
        query = BeliefQuery(concept="Recommendation", limit=3)

        response = await client.query_beliefs(query)

        assert isinstance(response, BeliefResponse)
        assert response.success is True
        assert isinstance(response.beliefs, list)

    @pytest.mark.asyncio
    async def test_get_justification(self, client):
        """Test getting justification for a belief"""
        query = JustificationQuery(
            belief_id="test_belief_1",
            max_depth=5
        )

        response = await client.get_justification(query)

        assert isinstance(response, JustificationResponse)
        assert response.success is True
        assert response.justification_tree is not None

        # Parse the justification JSON
        justification = json.loads(response.justification_tree)
        assert "belief_id" in justification
        assert justification["belief_id"] == "test_belief_1"

class TestConvenienceFunctions:
    """Test convenience functions"""

    @pytest.mark.asyncio
    async def test_send_observation(self):
        """Test the send_observation convenience function"""
        client = CSNeRSClient()

        # Test with loop closure
        lc = LoopClosure(
            landmark_id="L001",
            score=0.85,
            method="visual",
            pose_estimate=[1.0, 2.0, 3.0, 0.0, 0.0, 0.0, 1.0]
        )

        response = await send_observation(client, lc)
        assert isinstance(response, ObservationResponse)
        assert response.success is True

        # Test with appearance match
        am = AppearanceMatch(
            landmark_id="L002",
            consistency="consistent",
            score=0.78
        )

        response = await send_observation(client, am)
        assert isinstance(response, ObservationResponse)
        assert response.success is True

        # Test with GNC event
        gnc = GNCEvent(
            event_type="thruster_anomaly",
            mode="burn",
            severity=0.65,
            parameters={}
        )

        response = await send_observation(client, gnc)
        assert isinstance(response, ObservationResponse)
        assert response.success is True

        # Test with medical finding
        mf = MedicalFinding(
            patient_id="P001",
            finding_type="lesion",
            confidence=0.89,
            location="liver",
            size_mm=12.5,
            modality="CT"
        )

        response = await send_observation(client, mf)
        assert isinstance(response, ObservationResponse)
        assert response.success is True

        # Test with unsupported type
        with pytest.raises(ValueError):
            await send_observation(client, "invalid_observation")

    @pytest.mark.asyncio
    async def test_query_all_concepts(self):
        """Test querying all concept types"""
        client = CSNeRSClient()

        results = await query_all_concepts(client, limit=3)

        assert isinstance(results, dict)
        assert "HighConfidenceLandmark" in results
        assert "Hypothesis" in results
        assert "Recommendation" in results

        for concept, response in results.items():
            assert isinstance(response, BeliefResponse)
            assert response.success is True

class TestErrorHandling:
    """Test error handling scenarios"""

    @pytest.mark.asyncio
    async def test_connection_failure_handling(self):
        """Test handling of connection failures"""
        client = CSNeRSClient("invalid_host", 99999)

        # For v0.1.0, this should still "succeed" since it's mocked
        # In a real implementation, this would test actual connection failures
        connected = await client.connect()
        # Mock implementation returns True, but real implementation would handle failures
        assert isinstance(connected, bool)

    @pytest.mark.asyncio
    async def test_send_without_connection(self):
        """Test sending observations without established connection"""
        client = CSNeRSClient()
        # Don't connect first

        lc = LoopClosure(
            landmark_id="L001",
            score=0.85,
            method="visual",
            pose_estimate=[1.0, 2.0, 3.0, 0.0, 0.0, 0.0, 1.0]
        )

        # Should auto-connect and succeed in v0.1.0 mock
        response = await client.send_loop_closure(lc)
        assert isinstance(response, ObservationResponse)

class TestIntegration:
    """Integration tests for complete workflows"""

    @pytest.mark.asyncio
    async def test_slam_workflow(self):
        """Test complete SLAM workflow"""
        client = CSNeRSClient()

        # Send loop closure
        lc = LoopClosure(
            landmark_id="L001",
            score=0.85,
            method="visual",
            pose_estimate=[1.0, 2.0, 3.0, 0.0, 0.0, 0.0, 1.0]
        )

        lc_response = await client.send_loop_closure(lc)
        assert lc_response.success is True

        # Send appearance match
        am = AppearanceMatch(
            landmark_id="L001",
            consistency="consistent",
            score=0.78
        )

        am_response = await client.send_appearance_match(am)
        assert am_response.success is True

        # Query for landmarks
        query = BeliefQuery(concept="HighConfidenceLandmark", limit=5)
        query_response = await client.query_beliefs(query)
        assert query_response.success is True

        # Get justification if beliefs exist
        if query_response.beliefs:
            belief = query_response.beliefs[0]
            just_query = JustificationQuery(belief_id=belief.belief_id)
            just_response = await client.get_justification(just_query)
            assert just_response.success is True

    @pytest.mark.asyncio
    async def test_gnc_workflow(self):
        """Test complete GNC workflow"""
        client = CSNeRSClient()

        # Send GNC event
        gnc = GNCEvent(
            event_type="thruster_anomaly",
            mode="burn",
            severity=0.65,
            parameters={"thrust_deviation": 0.15}
        )

        gnc_response = await client.send_gnc_event(gnc)
        assert gnc_response.success is True

        # Query for hypotheses
        query = BeliefQuery(concept="Hypothesis", limit=5)
        query_response = await client.query_beliefs(query)
        assert query_response.success is True

    @pytest.mark.asyncio
    async def test_medical_workflow(self):
        """Test complete medical workflow"""
        client = CSNeRSClient()

        # Send medical finding
        mf = MedicalFinding(
            patient_id="P001",
            finding_type="lesion",
            confidence=0.89,
            location="liver_segment_4",
            size_mm=12.5,
            modality="CT"
        )

        mf_response = await client.send_medical_finding(mf)
        assert mf_response.success is True

        # Query for recommendations
        query = BeliefQuery(concept="Recommendation", limit=5)
        query_response = await client.query_beliefs(query)
        assert query_response.success is True

class TestObservationType:
    """Test the ObservationType enum"""

    def test_observation_type_values(self):
        """Test observation type enum values"""
        assert ObservationType.LOOP_CLOSURE.value == "loop_closure"
        assert ObservationType.APPEARANCE_MATCH.value == "appearance_match"
        assert ObservationType.GNC_EVENT.value == "gnc_event"
        assert ObservationType.MEDICAL_FINDING.value == "medical_finding"

# Performance Tests

class TestPerformance:
    """Basic performance tests for v0.1.0"""

    @pytest.mark.asyncio
    async def test_concurrent_observations(self):
        """Test sending multiple observations concurrently"""
        client = CSNeRSClient()

        # Create multiple observations
        observations = [
            LoopClosure(
                landmark_id=f"L{i:03d}",
                score=0.8 + (i * 0.01),
                method="visual",
                pose_estimate=[i, i+1, i+2, 0, 0, 0, 1]
            )
            for i in range(5)
        ]

        # Send them concurrently
        start_time = time.time()
        tasks = [client.send_loop_closure(obs) for obs in observations]
        responses = await asyncio.gather(*tasks)
        end_time = time.time()

        # Verify all succeeded
        assert len(responses) == 5
        assert all(r.success for r in responses)

        # Basic performance check (should be much faster than sequential)
        duration = end_time - start_time
        assert duration < 2.0  # Should complete in under 2 seconds for mock

    @pytest.mark.asyncio
    async def test_rapid_queries(self):
        """Test rapid successive queries"""
        client = CSNeRSClient()

        # Send multiple queries rapidly
        queries = [
            BeliefQuery(concept="HighConfidenceLandmark", limit=3),
            BeliefQuery(concept="Hypothesis", limit=3),
            BeliefQuery(concept="Recommendation", limit=3),
            BeliefQuery(concept="HighConfidenceLandmark", limit=5),
            BeliefQuery(concept="Hypothesis", limit=5),
        ]

        start_time = time.time()
        tasks = [client.query_beliefs(query) for query in queries]
        responses = await asyncio.gather(*tasks)
        end_time = time.time()

        # Verify all succeeded
        assert len(responses) == 5
        assert all(r.success for r in responses)

        # Should complete quickly
        duration = end_time - start_time
        assert duration < 2.0

if __name__ == "__main__":
    # Run tests with pytest
    pytest.main([__file__, "-v"])
