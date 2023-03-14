package org.hyades.metrics.processor;

import io.quarkus.test.junit.QuarkusTest;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TestOutputTopic;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.TopologyTestDriver;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.test.TestRecord;
import org.hyades.proto.KafkaProtobufDeserializer;
import org.hyades.proto.KafkaProtobufSerializer;
import org.hyades.proto.metrics.v1.FindingsMetrics;
import org.hyades.proto.metrics.v1.PolicyViolationsMetrics;
import org.hyades.proto.metrics.v1.ProjectMetrics;
import org.hyades.proto.metrics.v1.VulnerabilitiesMetrics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyades.proto.metrics.v1.Status.STATUS_CREATED;
import static org.hyades.proto.metrics.v1.Status.STATUS_DELETED;
import static org.hyades.proto.metrics.v1.Status.STATUS_UNCHANGED;
import static org.hyades.proto.metrics.v1.Status.STATUS_UPDATED;
import static org.hyades.proto.metrics.v1.VulnerabilityStatus.VULNERABILITY_STATUS_NOT_VULNERABLE;
import static org.hyades.proto.metrics.v1.VulnerabilityStatus.VULNERABILITY_STATUS_UNCHANGED;
import static org.hyades.proto.metrics.v1.VulnerabilityStatus.VULNERABILITY_STATUS_UNKNOWN;
import static org.hyades.proto.metrics.v1.VulnerabilityStatus.VULNERABILITY_STATUS_VULNERABLE;

@QuarkusTest
class ProjectDeltaProcessorTest {
    @Inject
    ProjectProcessorSupplier projectProcessorSupplier;
    private TopologyTestDriver testDriver;
    private TestInputTopic<String, ProjectMetrics> inputTopic;
    private TestOutputTopic<String, ProjectMetrics> outputTopic;

    private KeyValueStore<String, ProjectMetrics> store;

    private static final UUID PROJECT_UUID = UUID.randomUUID();

    @BeforeEach
    void beforeEach() {
        final var topology = new Topology();
        topology.addSource("sourceProcessor", new StringDeserializer(),
                new KafkaProtobufDeserializer<>(ProjectMetrics.parser()), "input-topic");
        topology.addProcessor("deltaProjectProcessor", projectProcessorSupplier, "sourceProcessor");
        topology.addSink("sinkProcessor", "output-topic",
                new StringSerializer(), new KafkaProtobufSerializer<>(), "deltaProjectProcessor");

        testDriver = new TopologyTestDriver(topology);
        store = testDriver.getKeyValueStore("delta-project-store");
        inputTopic = testDriver.createInputTopic("input-topic",
                new StringSerializer(), new KafkaProtobufSerializer<>());
        outputTopic = testDriver.createOutputTopic("output-topic",
                new StringDeserializer(), new KafkaProtobufDeserializer<>(ProjectMetrics.parser()));
    }

    @AfterEach
    void afterEach() {
        testDriver.close();
    }

    @Test
    void shouldReturnProjectMetricsAsDeltaIfMetricsIsNotInStore() {

        final TestRecord<String, ProjectMetrics> inputRecord = createTestRecord(2, 3, 4, 2);
        inputTopic.pipeInput(inputRecord);

        assertThat(outputTopic.getQueueSize()).isEqualTo(1);

        assertThat(outputTopic.readRecord()).satisfies(record -> {
            assertThat(record.key()).isEqualTo(inputRecord.getKey());
            assertThat(record.getValue().getProjectUuid()).isEqualTo(PROJECT_UUID.toString());
            assertThat(record.getValue().getStatus()).isEqualTo(STATUS_CREATED);
            assertThat(record.getValue().getVulnerabilityStatus()).isEqualTo(VULNERABILITY_STATUS_VULNERABLE);
            assertThat(record.getValue().getComponents()).isEqualTo(2);
            assertThat(record.getValue().getVulnerabilities().getCritical()).isEqualTo(2);
            assertThat(record.getValue().getVulnerabilities().getHigh()).isEqualTo(3);
            assertThat(record.getValue().getVulnerabilities().getMedium()).isEqualTo(4);
            assertThat(record.getValue().getVulnerabilities().getLow()).isEqualTo(5);
            assertThat(record.getValue().getVulnerabilities().getTotal()).isEqualTo(2);
            assertThat(record.getValue().getVulnerableComponents()).isEqualTo(2);
            assertThat(record.getValue().getFindings().getAudited()).isEqualTo(5);
            assertThat(record.getValue().getFindings().getUnaudited()).isEqualTo(5);
            assertThat(record.getValue().getFindings().getTotal()).isEqualTo(10);
            assertThat(record.getValue().getPolicyViolations().getFail()).isEqualTo(1);
            assertThat(record.getValue().getPolicyViolations().getWarn()).isEqualTo(5);
            assertThat(record.getValue().getPolicyViolations().getInfo()).isEqualTo(5);
            assertThat(record.getValue().getPolicyViolations().getAudited()).isZero();
            assertThat(record.getValue().getPolicyViolations().getUnaudited()).isZero();
            assertThat(record.getValue().getPolicyViolations().getSecurityAudited()).isEqualTo(5);
            assertThat(record.getValue().getPolicyViolations().getSecurityUnaudited()).isEqualTo(5);
            assertThat(record.getValue().getPolicyViolations().getSecurityTotal()).isEqualTo(10);
            assertThat(record.getValue().getPolicyViolations().getOperationalTotal()).isEqualTo(10);
            assertThat(record.getValue().getPolicyViolations().getOperationalUnaudited()).isEqualTo(5);
            assertThat(record.getValue().getPolicyViolations().getOperationalAudited()).isEqualTo(5);
            assertThat(record.getValue().getPolicyViolations().getLicenseAudited()).isEqualTo(5);
            assertThat(record.getValue().getPolicyViolations().getLicenseUnaudited()).isEqualTo(5);
            assertThat(record.getValue().getPolicyViolations().getLicenseTotal()).isEqualTo(10);
        });
    }

    @Test
    void shouldReturnDeltaWithOnlyChangesWhenEventIsOfSameProject() {

        final TestRecord<String, ProjectMetrics> inputRecord = createTestRecord(2, 3, 4, 2);
        inputTopic.pipeInput(inputRecord);
        final TestRecord<String, ProjectMetrics> inputRecord2 = createTestRecord(1, 2, 4, 2);
        inputTopic.pipeInput(inputRecord2);

        assertThat(outputTopic.getQueueSize()).isEqualTo(2);

        assertThat(outputTopic.readRecordsToList()).satisfiesExactlyInAnyOrder(
                record -> {
                    assertThat(record.getValue().getVulnerabilities().getCritical()).isEqualTo(2);
                    assertThat(record.getValue().getProjectUuid()).isEqualTo(inputRecord.getKey());
                    assertThat(record.getValue().getStatus()).isEqualTo(STATUS_CREATED);
                    assertThat(record.getValue().getVulnerabilityStatus()).isEqualTo(VULNERABILITY_STATUS_VULNERABLE);
                    assertThat(record.getValue().getComponents()).isEqualTo(2);
                    assertThat(record.getValue().getVulnerabilities().getCritical()).isEqualTo(2);
                    assertThat(record.getValue().getVulnerabilities().getHigh()).isEqualTo(3);
                    assertThat(record.getValue().getVulnerabilities().getMedium()).isEqualTo(4);
                    assertThat(record.getValue().getVulnerabilities().getLow()).isEqualTo(5);
                    assertThat(record.getValue().getVulnerabilities().getTotal()).isEqualTo(2);
                    assertThat(record.getValue().getVulnerableComponents()).isEqualTo(2);
                    assertThat(record.getValue().getFindings().getAudited()).isEqualTo(5);
                    assertThat(record.getValue().getFindings().getUnaudited()).isEqualTo(5);
                    assertThat(record.getValue().getFindings().getTotal()).isEqualTo(10);
                    assertThat(record.getValue().getPolicyViolations().getFail()).isEqualTo(1);
                    assertThat(record.getValue().getPolicyViolations().getWarn()).isEqualTo(5);
                    assertThat(record.getValue().getPolicyViolations().getInfo()).isEqualTo(5);
                    assertThat(record.getValue().getPolicyViolations().getAudited()).isZero();
                    assertThat(record.getValue().getPolicyViolations().getUnaudited()).isZero();
                    assertThat(record.getValue().getPolicyViolations().getSecurityAudited()).isEqualTo(5);
                    assertThat(record.getValue().getPolicyViolations().getSecurityUnaudited()).isEqualTo(5);
                    assertThat(record.getValue().getPolicyViolations().getSecurityTotal()).isEqualTo(10);
                    assertThat(record.getValue().getPolicyViolations().getOperationalTotal()).isEqualTo(10);
                    assertThat(record.getValue().getPolicyViolations().getOperationalUnaudited()).isEqualTo(5);
                    assertThat(record.getValue().getPolicyViolations().getOperationalAudited()).isEqualTo(5);
                    assertThat(record.getValue().getPolicyViolations().getLicenseAudited()).isEqualTo(5);
                    assertThat(record.getValue().getPolicyViolations().getLicenseUnaudited()).isEqualTo(5);
                    assertThat(record.getValue().getPolicyViolations().getLicenseTotal()).isEqualTo(10);
                },

                record -> {
                    assertThat(record.getValue().getVulnerabilities().getCritical()).isEqualTo(-1);
                    assertThat(record.getValue().getProjectUuid()).isEqualTo(inputRecord.getKey());
                    assertThat(record.getValue().getStatus()).isEqualTo(STATUS_UPDATED);
                    assertThat(record.getValue().getVulnerabilityStatus()).isEqualTo(VULNERABILITY_STATUS_UNCHANGED);
                    assertThat(record.getValue().getVulnerabilities().getHigh()).isEqualTo(-1);
                    assertThat(record.getValue().getVulnerabilities().getMedium()).isZero();
                    assertThat(record.getValue().getVulnerabilities().getLow()).isZero();
                    assertThat(record.getValue().getVulnerabilities().getTotal()).isZero();
                    assertThat(record.getValue().getFindings().getAudited()).isZero();
                    assertThat(record.getValue().getFindings().getUnaudited()).isZero();
                    assertThat(record.getValue().getFindings().getTotal()).isZero();
                    assertThat(record.getValue().getPolicyViolations().getFail()).isZero();
                    assertThat(record.getValue().getPolicyViolations().getInfo()).isZero();
                    assertThat(record.getValue().getPolicyViolations().getAudited()).isZero();
                    assertThat(record.getValue().getPolicyViolations().getUnaudited()).isZero();
                    assertThat(record.getValue().getPolicyViolations().getFail()).isZero();
                    assertThat(record.getValue().getPolicyViolations().getWarn()).isZero();
                    assertThat(record.getValue().getPolicyViolations().getInfo()).isZero();
                    assertThat(record.getValue().getPolicyViolations().getAudited()).isZero();
                    assertThat(record.getValue().getPolicyViolations().getUnaudited()).isZero();
                    assertThat(record.getValue().getPolicyViolations().getSecurityAudited()).isZero();
                    assertThat(record.getValue().getPolicyViolations().getSecurityUnaudited()).isZero();
                    assertThat(record.getValue().getPolicyViolations().getSecurityTotal()).isZero();
                    assertThat(record.getValue().getPolicyViolations().getOperationalTotal()).isZero();
                    assertThat(record.getValue().getPolicyViolations().getOperationalUnaudited()).isZero();
                    assertThat(record.getValue().getPolicyViolations().getOperationalAudited()).isZero();
                    assertThat(record.getValue().getPolicyViolations().getLicenseAudited()).isZero();
                    assertThat(record.getValue().getPolicyViolations().getLicenseUnaudited()).isZero();
                    assertThat(record.getValue().getPolicyViolations().getLicenseTotal()).isZero();
                }
        );
    }

    @Test
    void shouldRemoveProjectMetricsWhenTombstoneEventIsReceived() {

        final TestRecord<String, ProjectMetrics> inputRecord = createTestRecord(2, 3, 4, 2);
        inputTopic.pipeInput(inputRecord);
        inputTopic.pipeInput(new TestRecord<>(PROJECT_UUID.toString(), null));

        assertThat(outputTopic.readRecordsToList()).satisfiesExactlyInAnyOrder(
                record -> {
                    assertThat(record.getValue().getVulnerabilities().getCritical()).isEqualTo(2);
                    assertThat(record.getValue().getProjectUuid()).isEqualTo(inputRecord.getKey());
                    assertThat(record.getValue().getStatus()).isEqualTo(STATUS_CREATED);
                    assertThat(record.getValue().getVulnerabilityStatus()).isEqualTo(VULNERABILITY_STATUS_VULNERABLE);
                    assertThat(record.getValue().getComponents()).isEqualTo(2);
                    assertThat(record.getValue().getVulnerabilities().getCritical()).isEqualTo(2);
                    assertThat(record.getValue().getVulnerabilities().getHigh()).isEqualTo(3);
                    assertThat(record.getValue().getVulnerabilities().getMedium()).isEqualTo(4);
                    assertThat(record.getValue().getVulnerabilities().getLow()).isEqualTo(5);
                    assertThat(record.getValue().getVulnerabilities().getTotal()).isEqualTo(2);
                    assertThat(record.getValue().getVulnerableComponents()).isEqualTo(2);
                    assertThat(record.getValue().getFindings().getAudited()).isEqualTo(5);
                    assertThat(record.getValue().getFindings().getUnaudited()).isEqualTo(5);
                    assertThat(record.getValue().getFindings().getTotal()).isEqualTo(10);
                    assertThat(record.getValue().getPolicyViolations().getFail()).isEqualTo(1);
                    assertThat(record.getValue().getPolicyViolations().getWarn()).isEqualTo(5);
                    assertThat(record.getValue().getPolicyViolations().getInfo()).isEqualTo(5);
                    assertThat(record.getValue().getPolicyViolations().getAudited()).isZero();
                    assertThat(record.getValue().getPolicyViolations().getUnaudited()).isZero();
                    assertThat(record.getValue().getPolicyViolations().getSecurityAudited()).isEqualTo(5);
                    assertThat(record.getValue().getPolicyViolations().getSecurityUnaudited()).isEqualTo(5);
                    assertThat(record.getValue().getPolicyViolations().getSecurityTotal()).isEqualTo(10);
                    assertThat(record.getValue().getPolicyViolations().getOperationalTotal()).isEqualTo(10);
                    assertThat(record.getValue().getPolicyViolations().getOperationalUnaudited()).isEqualTo(5);
                    assertThat(record.getValue().getPolicyViolations().getOperationalAudited()).isEqualTo(5);
                    assertThat(record.getValue().getPolicyViolations().getLicenseAudited()).isEqualTo(5);
                    assertThat(record.getValue().getPolicyViolations().getLicenseUnaudited()).isEqualTo(5);
                    assertThat(record.getValue().getPolicyViolations().getLicenseTotal()).isEqualTo(10);
                },

                record -> {
                    assertThat(record.getValue().getProjectUuid()).isEqualTo(inputRecord.getKey());
                    assertThat(record.getValue().getStatus()).isEqualTo(STATUS_DELETED);
                    assertThat(record.getValue().getVulnerabilityStatus()).isEqualTo(VULNERABILITY_STATUS_UNKNOWN);
                    assertThat(record.getValue().getComponents()).isEqualTo(-2);
                    assertThat(record.getValue().getVulnerabilities().getCritical()).isEqualTo(-2);
                    assertThat(record.getValue().getVulnerabilities().getHigh()).isEqualTo(-3);
                    assertThat(record.getValue().getVulnerabilities().getMedium()).isEqualTo(-4);
                    assertThat(record.getValue().getVulnerabilities().getLow()).isEqualTo(-5);
                    assertThat(record.getValue().getVulnerabilities().getTotal()).isEqualTo(-2);
                    assertThat(record.getValue().getVulnerableComponents()).isEqualTo(-2);
                    assertThat(record.getValue().getFindings().getAudited()).isEqualTo(-5);
                    assertThat(record.getValue().getFindings().getUnaudited()).isEqualTo(-5);
                    assertThat(record.getValue().getFindings().getTotal()).isEqualTo(-10);
                    assertThat(record.getValue().getPolicyViolations().getFail()).isEqualTo(-1);
                    assertThat(record.getValue().getPolicyViolations().getWarn()).isEqualTo(-5);
                    assertThat(record.getValue().getPolicyViolations().getInfo()).isEqualTo(-5);
                    assertThat(record.getValue().getPolicyViolations().getAudited()).isZero();
                    assertThat(record.getValue().getPolicyViolations().getUnaudited()).isZero();
                    assertThat(record.getValue().getPolicyViolations().getSecurityAudited()).isEqualTo(-5);
                    assertThat(record.getValue().getPolicyViolations().getSecurityUnaudited()).isEqualTo(-5);
                    assertThat(record.getValue().getPolicyViolations().getSecurityTotal()).isEqualTo(-10);
                    assertThat(record.getValue().getPolicyViolations().getOperationalTotal()).isEqualTo(-10);
                    assertThat(record.getValue().getPolicyViolations().getOperationalUnaudited()).isEqualTo(-5);
                    assertThat(record.getValue().getPolicyViolations().getOperationalAudited()).isEqualTo(-5);
                    assertThat(record.getValue().getPolicyViolations().getLicenseAudited()).isEqualTo(-5);
                    assertThat(record.getValue().getPolicyViolations().getLicenseUnaudited()).isEqualTo(-5);
                    assertThat(record.getValue().getPolicyViolations().getLicenseTotal()).isEqualTo(-10);

                }
        );
    }

    @Test
    void shouldSendNoChangeStatusIfNoChangeFromProjectMetricsInStore() {

        store.put(PROJECT_UUID.toString(), createProjectMetrics(2, 3, 4, 2));

        final TestRecord<String, ProjectMetrics> inputRecord = createTestRecord(2, 3, 4, 2);
        inputTopic.pipeInput(inputRecord);

        assertThat(outputTopic.readRecord()).satisfies(record -> {
            assertThat(record.key()).isEqualTo(inputRecord.getKey());
            assertThat(record.getValue().getStatus()).isEqualTo(STATUS_UNCHANGED);
            assertThat(record.getValue().getVulnerabilityStatus()).isEqualTo(VULNERABILITY_STATUS_UNCHANGED);
            assertThat(record.getValue().getComponents()).isZero();
            assertThat(record.getValue().getVulnerableComponents()).isZero();
            assertThat(record.getValue().getVulnerabilities().getCritical()).isZero();
            assertThat(record.getValue().getVulnerabilities().getHigh()).isZero();
            assertThat(record.getValue().getVulnerabilities().getMedium()).isZero();
            assertThat(record.getValue().getVulnerabilities().getLow()).isZero();
            assertThat(record.getValue().getVulnerabilities().getTotal()).isZero();
            assertThat(record.getValue().getVulnerableComponents()).isZero();
            assertThat(record.getValue().getFindings().getAudited()).isZero();
            assertThat(record.getValue().getFindings().getUnaudited()).isZero();
            assertThat(record.getValue().getFindings().getTotal()).isZero();
            assertThat(record.getValue().getPolicyViolations().getFail()).isZero();
            assertThat(record.getValue().getPolicyViolations().getWarn()).isZero();
            assertThat(record.getValue().getPolicyViolations().getInfo()).isZero();
            assertThat(record.getValue().getPolicyViolations().getAudited()).isZero();
            assertThat(record.getValue().getPolicyViolations().getUnaudited()).isZero();
            assertThat(record.getValue().getPolicyViolations().getSecurityAudited()).isZero();
            assertThat(record.getValue().getPolicyViolations().getSecurityUnaudited()).isZero();
            assertThat(record.getValue().getPolicyViolations().getSecurityTotal()).isZero();
            assertThat(record.getValue().getPolicyViolations().getOperationalTotal()).isZero();
            assertThat(record.getValue().getPolicyViolations().getOperationalUnaudited()).isZero();
            assertThat(record.getValue().getPolicyViolations().getOperationalAudited()).isZero();
            assertThat(record.getValue().getPolicyViolations().getLicenseAudited()).isZero();
            assertThat(record.getValue().getPolicyViolations().getLicenseUnaudited()).isZero();
            assertThat(record.getValue().getPolicyViolations().getLicenseTotal()).isZero();
        });
    }

    @Test
    void shouldSendDeltaOfMetricsFromProjectMetricsInStore() {

        store.put(PROJECT_UUID.toString(), createProjectMetrics(2, 3, 4, 2));

        final TestRecord<String, ProjectMetrics> inputRecord = createRecordWithComponentDeleted();
        inputTopic.pipeInput(inputRecord);

        assertThat(outputTopic.readRecord()).satisfies(record -> {
            assertThat(record.key()).isEqualTo(inputRecord.getKey());
            assertThat(record.getValue().getStatus()).isEqualTo(STATUS_UPDATED);
            assertThat(record.getValue().getVulnerabilityStatus()).isEqualTo(VULNERABILITY_STATUS_NOT_VULNERABLE);
            assertThat(record.getValue().getComponents()).isEqualTo(-1);
            assertThat(record.getValue().getVulnerableComponents()).isZero();
            assertThat(record.getValue().getVulnerabilities().getCritical()).isEqualTo(-2);
            assertThat(record.getValue().getVulnerabilities().getHigh()).isEqualTo(-3);
            assertThat(record.getValue().getVulnerabilities().getMedium()).isEqualTo(-4);
            assertThat(record.getValue().getVulnerabilities().getLow()).isZero();
            assertThat(record.getValue().getVulnerabilities().getTotal()).isEqualTo(-2);
            assertThat(record.getValue().getVulnerableComponents()).isZero();
            assertThat(record.getValue().getFindings().getAudited()).isZero();
            assertThat(record.getValue().getFindings().getUnaudited()).isZero();
            assertThat(record.getValue().getFindings().getTotal()).isZero();
            assertThat(record.getValue().getPolicyViolations().getFail()).isZero();
            assertThat(record.getValue().getPolicyViolations().getWarn()).isZero();
            assertThat(record.getValue().getPolicyViolations().getInfo()).isZero();
            assertThat(record.getValue().getPolicyViolations().getAudited()).isZero();
            assertThat(record.getValue().getPolicyViolations().getUnaudited()).isZero();
            assertThat(record.getValue().getPolicyViolations().getSecurityAudited()).isZero();
            assertThat(record.getValue().getPolicyViolations().getSecurityUnaudited()).isZero();
            assertThat(record.getValue().getPolicyViolations().getSecurityTotal()).isZero();
            assertThat(record.getValue().getPolicyViolations().getOperationalTotal()).isZero();
            assertThat(record.getValue().getPolicyViolations().getOperationalUnaudited()).isZero();
            assertThat(record.getValue().getPolicyViolations().getOperationalAudited()).isZero();
            assertThat(record.getValue().getPolicyViolations().getLicenseAudited()).isZero();
            assertThat(record.getValue().getPolicyViolations().getLicenseUnaudited()).isZero();
            assertThat(record.getValue().getPolicyViolations().getLicenseTotal()).isZero();
        });
    }

    @Test
    void shouldSetVulnerabilityStatusToNoChangeIfVulnerableProjectRemainsSo() {
        store.put(PROJECT_UUID.toString(), createProjectMetrics(2, 3, 4, 2));

        final TestRecord<String, ProjectMetrics> inputRecord = createTestRecord(0, 3, 4, 2);
        inputTopic.pipeInput(inputRecord);

        assertThat(outputTopic.readRecord()).satisfies(record -> {
            assertThat(record.key()).isEqualTo(inputRecord.getKey());
            assertThat(record.getValue().getVulnerabilityStatus()).isEqualTo(VULNERABILITY_STATUS_UNCHANGED);
        });

    }

    @Test
    void shouldSetVulnerabilityStatusToNotVulnerableIfVulnerabilitiesFixed() {
        store.put(PROJECT_UUID.toString(), createProjectMetrics(2, 3, 4, 9));

        final TestRecord<String, ProjectMetrics> inputRecord = createTestRecord(0, 0, 0, 0);
        inputTopic.pipeInput(inputRecord);

        assertThat(outputTopic.readRecord()).satisfies(record -> {
            assertThat(record.key()).isEqualTo(inputRecord.getKey());
            assertThat(record.getValue().getVulnerabilityStatus()).isEqualTo(VULNERABILITY_STATUS_NOT_VULNERABLE);
        });
    }

    private TestRecord<String, ProjectMetrics> createTestRecord(int critical, int high, int medium, int vulnerabilities) {
        final var projectMetrics = createProjectMetrics(critical, high, medium, vulnerabilities);
        return new TestRecord<>(PROJECT_UUID.toString(), projectMetrics);
    }

    private TestRecord<String, ProjectMetrics> createRecordWithComponentDeleted() {
        final var projectMetrics = ProjectMetrics.newBuilder()
                .setProjectUuid(PROJECT_UUID.toString())
                .setComponents(1)
                .setVulnerableComponents(2)
                .setVulnerabilities(VulnerabilitiesMetrics.newBuilder()
                        .setLow(5))
                .setFindings(FindingsMetrics.newBuilder()
                        .setTotal(10)
                        .setAudited(5)
                        .setUnaudited(5))
                .setPolicyViolations(PolicyViolationsMetrics.newBuilder()
                        .setFail(1)
                        .setWarn(5)
                        .setInfo(5)
                        .setLicenseTotal(10)
                        .setLicenseAudited(5)
                        .setLicenseUnaudited(5)
                        .setOperationalTotal(10)
                        .setOperationalAudited(5)
                        .setOperationalUnaudited(5)
                        .setSecurityTotal(10)
                        .setSecurityAudited(5)
                        .setSecurityUnaudited(5))
                .build();
        return new TestRecord<>(PROJECT_UUID.toString(), projectMetrics);
    }

    private static ProjectMetrics createProjectMetrics(int critical, int high, int medium, int vulnerabilities) {
        return ProjectMetrics.newBuilder()
                .setProjectUuid(PROJECT_UUID.toString())
                .setComponents(2)
                .setVulnerableComponents(2)
                .setVulnerabilities(VulnerabilitiesMetrics.newBuilder()
                        .setTotal(vulnerabilities)
                        .setCritical(critical)
                        .setHigh(high)
                        .setMedium(medium)
                        .setLow(5))
                .setFindings(FindingsMetrics.newBuilder()
                        .setTotal(10)
                        .setAudited(5)
                        .setUnaudited(5))
                .setPolicyViolations(PolicyViolationsMetrics.newBuilder()
                        .setFail(1)
                        .setWarn(5)
                        .setInfo(5)
                        .setLicenseTotal(10)
                        .setLicenseAudited(5)
                        .setLicenseUnaudited(5)
                        .setOperationalTotal(10)
                        .setOperationalAudited(5)
                        .setOperationalUnaudited(5)
                        .setSecurityTotal(10)
                        .setSecurityAudited(5)
                        .setSecurityUnaudited(5))
                .build();
    }
}