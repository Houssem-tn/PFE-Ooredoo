package com.pfe.zm_dashboard.service;

import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoCollection;
import com.pfe.MainPackage.Repository.NotificationRepository;
import com.pfe.MainPackage.Repository.UserRepository;
import com.pfe.MainPackage.Service.UserService;
import com.pfe.case_management.repository.ZmFieldImportAuditRepository;
import com.pfe.stock_management.material_return.MaterialReturnRepository;
import com.pfe.stock_management.repository.CaseStockConsumptionRepository;
import com.pfe.stock_management.repository.DpmRepository;
import com.pfe.stock_management.repository.EquipmentInstanceRepository;
import com.pfe.stock_management.repository.StockThresholdRepository;
import com.pfe.stock_management.repository.SttStockRepository;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ZmDashboardServiceTest {

    @Mock
    private MongoTemplate mongoTemplate;
    @Mock
    private UserRepository userRepository;
    @Mock
    private UserService userService;
    @Mock
    private DpmRepository dpmRepository;
    @Mock
    private MaterialReturnRepository materialReturnRepository;
    @Mock
    private SttStockRepository sttStockRepository;
    @Mock
    private CaseStockConsumptionRepository caseStockConsumptionRepository;
    @Mock
    private EquipmentInstanceRepository equipmentInstanceRepository;
    @Mock
    private StockThresholdRepository stockThresholdRepository;
    @Mock
    private ZmFieldImportAuditRepository importAuditRepository;
    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private MongoCollection<Document> casesCollection;
    @Mock
    private AggregateIterable<Document> aggregateIterable;

    @Test
    void runCaseFacet_buildsAggregationWhenNullComparisonsAreNeeded() {
        ZmDashboardService service = newService();
        Document expected = new Document("ok", true);

        when(mongoTemplate.getCollection("cases")).thenReturn(casesCollection);
        when(casesCollection.aggregate(anyList())).thenReturn(aggregateIterable);
        when(aggregateIterable.allowDiskUse(true)).thenReturn(aggregateIterable);
        when(aggregateIterable.first()).thenReturn(expected);

        Document result = assertDoesNotThrow(() -> invokeRunCaseFacet(service));

        assertEquals(expected, result);
        verify(casesCollection).aggregate(anyList());
    }

    private ZmDashboardService newService() {
        return new ZmDashboardService(
                mongoTemplate,
                userRepository,
                userService,
                dpmRepository,
                materialReturnRepository,
                sttStockRepository,
                caseStockConsumptionRepository,
                equipmentInstanceRepository,
                stockThresholdRepository,
                importAuditRepository,
                notificationRepository
        );
    }

    private static Document invokeRunCaseFacet(ZmDashboardService service) throws Exception {
        Method method = ZmDashboardService.class.getDeclaredMethod("runCaseFacet", String.class, Date.class, Instant.class);
        method.setAccessible(true);
        return (Document) method.invoke(service, "zm@example.com", new Date(), Instant.parse("2026-04-01T00:00:00Z"));
    }
}
