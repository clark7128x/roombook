# Індекс доказів

Згенеровано `python ops/audit/coverage.py`. Наявність тегу показує зв'язок із тестовим класом, а не результат приймання всіх положень ID.

Зареєстровано 316 ID; теги є для 221; без Java-тегу: 95.

FR-24 не входить у реалізацію: етап 6, Q-04 явно відхиляє самостійну зміну пароля. Індекс доповнено A-66…A-72 з етапу 9. Документи дизайну не змінювалися.

TS-22…TS-26 мають окремі експлуатаційні й браузерні звіти. Припущення про інфраструктуру та рішення власника не можна підтвердити додаванням тегів. Критерій T-30 «тест із тегом на кожний ID» залишається невиконаним, доки наведені прогалини не усунуто або їхній спосіб приймання явно не погоджено.

## ID без Java-тегу

NFR-02, NFR-04, NFR-05, OPS-07, OPS-08, OPS-11, OPS-12, TS-22, TS-23, TS-24, TS-25, TS-26, A-01, A-02, A-03, A-04, A-05, A-07, A-08, A-09, A-10, A-11, A-12, A-13, A-18, A-20, A-21, A-22, A-26, A-27, A-28, A-29, A-35, A-39, A-40, A-41, A-44, A-45, A-46, A-47, A-49, A-50, A-51, A-52, A-54, A-57, A-58, A-59, A-60, A-61, A-62, A-63, A-64, A-66, A-69, A-70, A-71, D-02, D-06, D-07, D-08, D-11, D-13, D-14, D-15, D-16, D-17, D-18, D-21, D-23, D-25, D-26, D-27, D-28, D-30, R-01, R-02, R-03, R-04, R-05, R-06, R-07, R-08, R-09, R-10, R-11, R-12, R-13, R-14, R-15, R-16, UI-03, UI-08, UI-10, UI-11

## Автоматичні тести

| ID | Класи з тегом |
|---|---|
| FR-01 | [UC01AuthenticationTest](../src/test/java/roombook/acceptance/UC01AuthenticationTest.java) |
| FR-02 | [UC04AdminCancelBookingTest](../src/test/java/roombook/acceptance/UC04AdminCancelBookingTest.java), [AppUserTest](../src/test/java/roombook/accounts/domain/AppUserTest.java) |
| FR-03 | [UC02CreateBookingTest](../src/test/java/roombook/acceptance/UC02CreateBookingTest.java), [ViewRequirementsTest](../src/test/java/roombook/acceptance/ViewRequirementsTest.java) |
| FR-04 | [UC02CreateBookingTest](../src/test/java/roombook/acceptance/UC02CreateBookingTest.java), [ViewRequirementsTest](../src/test/java/roombook/acceptance/ViewRequirementsTest.java) |
| FR-05 | [ViewRequirementsTest](../src/test/java/roombook/acceptance/ViewRequirementsTest.java) |
| FR-06 | [ViewRequirementsTest](../src/test/java/roombook/acceptance/ViewRequirementsTest.java) |
| FR-07 | [UC03CancelOwnBookingTest](../src/test/java/roombook/acceptance/UC03CancelOwnBookingTest.java), [ViewRequirementsTest](../src/test/java/roombook/acceptance/ViewRequirementsTest.java) |
| FR-08 | [UC04AdminCancelBookingTest](../src/test/java/roombook/acceptance/UC04AdminCancelBookingTest.java), [ViewRequirementsTest](../src/test/java/roombook/acceptance/ViewRequirementsTest.java) |
| FR-09 | [UC02CreateBookingTest](../src/test/java/roombook/acceptance/UC02CreateBookingTest.java), [BookingQueriesTest](../src/test/java/roombook/scheduling/application/BookingQueriesTest.java), [BookingServiceTest](../src/test/java/roombook/scheduling/application/BookingServiceTest.java), [BookingApiTest](../src/test/java/roombook/scheduling/web/api/BookingApiTest.java) |
| FR-10 | [UC02CreateBookingTest](../src/test/java/roombook/acceptance/UC02CreateBookingTest.java), [BookingServiceTest](../src/test/java/roombook/scheduling/application/BookingServiceTest.java), [BookingTest](../src/test/java/roombook/scheduling/domain/BookingTest.java) |
| FR-11 | [UC02CreateBookingTest](../src/test/java/roombook/acceptance/UC02CreateBookingTest.java), [BookingServiceTest](../src/test/java/roombook/scheduling/application/BookingServiceTest.java), [BookingTimePolicyTest](../src/test/java/roombook/scheduling/domain/BookingTimePolicyTest.java) |
| FR-12 | [UC02CreateBookingTest](../src/test/java/roombook/acceptance/UC02CreateBookingTest.java), [BookingServiceTest](../src/test/java/roombook/scheduling/application/BookingServiceTest.java), [BookingIntervalTest](../src/test/java/roombook/scheduling/domain/BookingIntervalTest.java), [EmployeeUiTest](../src/test/java/roombook/scheduling/web/ui/EmployeeUiTest.java), [DatabaseSchemaMigrationTest](../src/test/java/roombook/testsupport/DatabaseSchemaMigrationTest.java) |
| FR-13 | [UC02CreateBookingTest](../src/test/java/roombook/acceptance/UC02CreateBookingTest.java), [BookingServiceTest](../src/test/java/roombook/scheduling/application/BookingServiceTest.java), [BookingTimePolicyTest](../src/test/java/roombook/scheduling/domain/BookingTimePolicyTest.java) |
| FR-14 | [UC02CreateBookingTest](../src/test/java/roombook/acceptance/UC02CreateBookingTest.java), [BookingServiceTest](../src/test/java/roombook/scheduling/application/BookingServiceTest.java), [BookingTimePolicyTest](../src/test/java/roombook/scheduling/domain/BookingTimePolicyTest.java) |
| FR-15 | [UC02CreateBookingTest](../src/test/java/roombook/acceptance/UC02CreateBookingTest.java), [BookingServiceTest](../src/test/java/roombook/scheduling/application/BookingServiceTest.java), [EmployeeUiTest](../src/test/java/roombook/scheduling/web/ui/EmployeeUiTest.java) |
| FR-16 | [UC02CreateBookingTest](../src/test/java/roombook/acceptance/UC02CreateBookingTest.java), [BookingServiceTest](../src/test/java/roombook/scheduling/application/BookingServiceTest.java) |
| FR-17 | [UC03CancelOwnBookingTest](../src/test/java/roombook/acceptance/UC03CancelOwnBookingTest.java), [UC04AdminCancelBookingTest](../src/test/java/roombook/acceptance/UC04AdminCancelBookingTest.java), [BookingServiceTest](../src/test/java/roombook/scheduling/application/BookingServiceTest.java), [BookingTest](../src/test/java/roombook/scheduling/domain/BookingTest.java), [BookingApiTest](../src/test/java/roombook/scheduling/web/api/BookingApiTest.java) |
| FR-18 | [UC03CancelOwnBookingTest](../src/test/java/roombook/acceptance/UC03CancelOwnBookingTest.java), [BookingServiceTest](../src/test/java/roombook/scheduling/application/BookingServiceTest.java), [BookingTest](../src/test/java/roombook/scheduling/domain/BookingTest.java), [BookingApiTest](../src/test/java/roombook/scheduling/web/api/BookingApiTest.java) |
| FR-19 | [UC05ManageRoomsTest](../src/test/java/roombook/acceptance/UC05ManageRoomsTest.java), [RoomServiceTest](../src/test/java/roombook/scheduling/application/RoomServiceTest.java), [RoomTest](../src/test/java/roombook/scheduling/domain/RoomTest.java) |
| FR-20 | [UC05ManageRoomsTest](../src/test/java/roombook/acceptance/UC05ManageRoomsTest.java), [RoomServiceTest](../src/test/java/roombook/scheduling/application/RoomServiceTest.java), [RoomTest](../src/test/java/roombook/scheduling/domain/RoomTest.java) |
| FR-21 | [UC05ManageRoomsTest](../src/test/java/roombook/acceptance/UC05ManageRoomsTest.java), [RoomServiceTest](../src/test/java/roombook/scheduling/application/RoomServiceTest.java), [RoomTest](../src/test/java/roombook/scheduling/domain/RoomTest.java) |
| FR-22 | [UC05ManageRoomsTest](../src/test/java/roombook/acceptance/UC05ManageRoomsTest.java), [RoomServiceTest](../src/test/java/roombook/scheduling/application/RoomServiceTest.java), [RoomTest](../src/test/java/roombook/scheduling/domain/RoomTest.java) |
| FR-23 | [SecurityRequirementsTest](../src/test/java/roombook/acceptance/SecurityRequirementsTest.java), [UC06ManageUsersTest](../src/test/java/roombook/acceptance/UC06ManageUsersTest.java), [AccountQueriesTest](../src/test/java/roombook/accounts/application/AccountQueriesTest.java), [AccountServiceTest](../src/test/java/roombook/accounts/application/AccountServiceTest.java), [AdminPolicyTest](../src/test/java/roombook/accounts/domain/AdminPolicyTest.java), [AppUserTest](../src/test/java/roombook/accounts/domain/AppUserTest.java), [InvitationHttpTest](../src/test/java/roombook/accounts/web/InvitationHttpTest.java), [UserApiTest](../src/test/java/roombook/accounts/web/api/UserApiTest.java) |
| NFR-01 | [RequestIdFilterTest](../src/test/java/roombook/platform/RequestIdFilterTest.java), [RequestLoggingFilterTest](../src/test/java/roombook/platform/RequestLoggingFilterTest.java) |
| NFR-03 | [ConcurrencyRequirementsTest](../src/test/java/roombook/acceptance/ConcurrencyRequirementsTest.java) |
| NFR-06 | [SecurityRequirementsTest](../src/test/java/roombook/acceptance/SecurityRequirementsTest.java) |
| NFR-07 | [TimeRulesTest](../src/test/java/roombook/acceptance/TimeRulesTest.java) |
| INV-01 | [BookingTest](../src/test/java/roombook/scheduling/domain/BookingTest.java) |
| INV-02 | [BookingTest](../src/test/java/roombook/scheduling/domain/BookingTest.java), [BookingTimePolicyTest](../src/test/java/roombook/scheduling/domain/BookingTimePolicyTest.java), [SchedulingPersistenceTest](../src/test/java/roombook/scheduling/persistence/SchedulingPersistenceTest.java), [DatabaseSchemaMigrationTest](../src/test/java/roombook/testsupport/DatabaseSchemaMigrationTest.java) |
| INV-03 | [BookingRulesTest](../src/test/java/roombook/scheduling/domain/BookingRulesTest.java), [BookingTimePolicyTest](../src/test/java/roombook/scheduling/domain/BookingTimePolicyTest.java) |
| INV-04 | [BookingRulesTest](../src/test/java/roombook/scheduling/domain/BookingRulesTest.java), [BookingTimePolicyTest](../src/test/java/roombook/scheduling/domain/BookingTimePolicyTest.java) |
| INV-05 | [BookingRulesTest](../src/test/java/roombook/scheduling/domain/BookingRulesTest.java), [BookingTimePolicyTest](../src/test/java/roombook/scheduling/domain/BookingTimePolicyTest.java) |
| INV-06 | [BookingTest](../src/test/java/roombook/scheduling/domain/BookingTest.java) |
| INV-07 | [BookingTest](../src/test/java/roombook/scheduling/domain/BookingTest.java), [DatabaseSchemaMigrationTest](../src/test/java/roombook/testsupport/DatabaseSchemaMigrationTest.java), [InvariantAuditTest](../src/test/java/roombook/testsupport/InvariantAuditTest.java) |
| INV-08 | [ConcurrencyRequirementsTest](../src/test/java/roombook/acceptance/ConcurrencyRequirementsTest.java), [BookingServiceTest](../src/test/java/roombook/scheduling/application/BookingServiceTest.java), [SchedulingConcurrencyTest](../src/test/java/roombook/scheduling/application/SchedulingConcurrencyTest.java), [SchedulingPersistenceTest](../src/test/java/roombook/scheduling/persistence/SchedulingPersistenceTest.java), [BookingApiTest](../src/test/java/roombook/scheduling/web/api/BookingApiTest.java), [DatabaseSchemaMigrationTest](../src/test/java/roombook/testsupport/DatabaseSchemaMigrationTest.java), [InvariantAuditTest](../src/test/java/roombook/testsupport/InvariantAuditTest.java) |
| INV-09 | [ConcurrencyRequirementsTest](../src/test/java/roombook/acceptance/ConcurrencyRequirementsTest.java), [RoomServiceTest](../src/test/java/roombook/scheduling/application/RoomServiceTest.java), [SchedulingConcurrencyTest](../src/test/java/roombook/scheduling/application/SchedulingConcurrencyTest.java), [SchedulingPersistenceTest](../src/test/java/roombook/scheduling/persistence/SchedulingPersistenceTest.java), [InvariantAuditTest](../src/test/java/roombook/testsupport/InvariantAuditTest.java) |
| INV-10 | [DatabaseSchemaMigrationTest](../src/test/java/roombook/testsupport/DatabaseSchemaMigrationTest.java), [InvariantAuditTest](../src/test/java/roombook/testsupport/InvariantAuditTest.java) |
| INV-11 | [ComparisonKeyTest](../src/test/java/roombook/kernel/ComparisonKeyTest.java), [RoomServiceTest](../src/test/java/roombook/scheduling/application/RoomServiceTest.java), [RoomTest](../src/test/java/roombook/scheduling/domain/RoomTest.java), [SchedulingPersistenceTest](../src/test/java/roombook/scheduling/persistence/SchedulingPersistenceTest.java), [DatabaseSchemaMigrationTest](../src/test/java/roombook/testsupport/DatabaseSchemaMigrationTest.java) |
| INV-12 | [RoomServiceTest](../src/test/java/roombook/scheduling/application/RoomServiceTest.java), [RoomTest](../src/test/java/roombook/scheduling/domain/RoomTest.java) |
| INV-13 | [AppUserTest](../src/test/java/roombook/accounts/domain/AppUserTest.java), [AccountsPersistenceTest](../src/test/java/roombook/accounts/persistence/AccountsPersistenceTest.java), [ComparisonKeyTest](../src/test/java/roombook/kernel/ComparisonKeyTest.java), [DatabaseSchemaMigrationTest](../src/test/java/roombook/testsupport/DatabaseSchemaMigrationTest.java) |
| INV-14 | [SecurityRequirementsTest](../src/test/java/roombook/acceptance/SecurityRequirementsTest.java), [AccountConcurrencyTest](../src/test/java/roombook/accounts/application/AccountConcurrencyTest.java), [AccountServiceTest](../src/test/java/roombook/accounts/application/AccountServiceTest.java), [AdminPolicyTest](../src/test/java/roombook/accounts/domain/AdminPolicyTest.java), [AccountsPersistenceTest](../src/test/java/roombook/accounts/persistence/AccountsPersistenceTest.java), [UserApiTest](../src/test/java/roombook/accounts/web/api/UserApiTest.java), [InvariantAuditTest](../src/test/java/roombook/testsupport/InvariantAuditTest.java) |
| TR-01 | [TimeRulesTest](../src/test/java/roombook/acceptance/TimeRulesTest.java), [OfficeClockTest](../src/test/java/roombook/kernel/OfficeClockTest.java), [BookingApiTest](../src/test/java/roombook/scheduling/web/api/BookingApiTest.java), [RoomApiTest](../src/test/java/roombook/scheduling/web/api/RoomApiTest.java), [TimezoneExecutionTest](../src/test/java/roombook/testsupport/TimezoneExecutionTest.java) |
| TR-02 | [TimeRulesTest](../src/test/java/roombook/acceptance/TimeRulesTest.java), [OfficeClockTest](../src/test/java/roombook/kernel/OfficeClockTest.java), [SchedulingPersistenceTest](../src/test/java/roombook/scheduling/persistence/SchedulingPersistenceTest.java), [UiShellTest](../src/test/java/roombook/scheduling/web/ui/UiShellTest.java) |
| TR-03 | [OfficeClockTest](../src/test/java/roombook/kernel/OfficeClockTest.java), [ClockConfigurationTest](../src/test/java/roombook/platform/ClockConfigurationTest.java) |
| TR-04 | [TimeRulesTest](../src/test/java/roombook/acceptance/TimeRulesTest.java), [OfficeClockTest](../src/test/java/roombook/kernel/OfficeClockTest.java), [BookingTimePolicyTest](../src/test/java/roombook/scheduling/domain/BookingTimePolicyTest.java) |
| TR-05 | [TimeRulesTest](../src/test/java/roombook/acceptance/TimeRulesTest.java), [BookingQueriesTest](../src/test/java/roombook/scheduling/application/BookingQueriesTest.java), [BookingIntervalTest](../src/test/java/roombook/scheduling/domain/BookingIntervalTest.java), [BookingTest](../src/test/java/roombook/scheduling/domain/BookingTest.java) |
| TR-06 | [OfficeClockTest](../src/test/java/roombook/kernel/OfficeClockTest.java) |
| AR-01 | [ArchitectureTest](../src/test/java/roombook/ArchitectureTest.java) |
| AR-02 | [ArchitectureTest](../src/test/java/roombook/ArchitectureTest.java) |
| AR-03 | [ArchitectureTest](../src/test/java/roombook/ArchitectureTest.java) |
| AR-04 | [ArchitectureTest](../src/test/java/roombook/ArchitectureTest.java) |
| AR-05 | [ArchitectureTest](../src/test/java/roombook/ArchitectureTest.java) |
| AR-06 | [ArchitectureTest](../src/test/java/roombook/ArchitectureTest.java) |
| AR-07 | [ArchitectureTest](../src/test/java/roombook/ArchitectureTest.java) |
| AR-08 | [ArchitectureTest](../src/test/java/roombook/ArchitectureTest.java), [ClockConfigurationTest](../src/test/java/roombook/platform/ClockConfigurationTest.java) |
| AR-09 | [ArchitectureTest](../src/test/java/roombook/ArchitectureTest.java) |
| AR-10 | [AccountsPersistenceTest](../src/test/java/roombook/accounts/persistence/AccountsPersistenceTest.java), [SchedulingPersistenceTest](../src/test/java/roombook/scheduling/persistence/SchedulingPersistenceTest.java) |
| AR-11 | [BookingServiceTest](../src/test/java/roombook/scheduling/application/BookingServiceTest.java) |
| AR-12 | [AccountsPersistenceTest](../src/test/java/roombook/accounts/persistence/AccountsPersistenceTest.java), [ConstraintNamesTest](../src/test/java/roombook/platform/ConstraintNamesTest.java), [SchedulingPersistenceTest](../src/test/java/roombook/scheduling/persistence/SchedulingPersistenceTest.java) |
| AR-13 | [ArchitectureTest](../src/test/java/roombook/ArchitectureTest.java) |
| AR-14 | [ArchitectureTest](../src/test/java/roombook/ArchitectureTest.java) |
| AR-15 | [ArchitectureTest](../src/test/java/roombook/ArchitectureTest.java) |
| SEC-01 | [AuthenticationTest](../src/test/java/roombook/accounts/security/AuthenticationTest.java) |
| SEC-02 | [AccountAuthenticationProviderTest](../src/test/java/roombook/accounts/security/AccountAuthenticationProviderTest.java), [AuthenticationTest](../src/test/java/roombook/accounts/security/AuthenticationTest.java) |
| SEC-03 | [AppUserTest](../src/test/java/roombook/accounts/domain/AppUserTest.java), [PasswordPolicyTest](../src/test/java/roombook/accounts/domain/PasswordPolicyTest.java), [AccountAuthenticationProviderTest](../src/test/java/roombook/accounts/security/AccountAuthenticationProviderTest.java), [InvitationHttpTest](../src/test/java/roombook/accounts/web/InvitationHttpTest.java), [UserApiTest](../src/test/java/roombook/accounts/web/api/UserApiTest.java), [AdminUiTest](../src/test/java/roombook/scheduling/web/ui/AdminUiTest.java) |
| SEC-04 | [AdminBootstrapTest](../src/test/java/roombook/accounts/application/AdminBootstrapTest.java), [AppUserTest](../src/test/java/roombook/accounts/domain/AppUserTest.java), [AuthenticationTest](../src/test/java/roombook/accounts/security/AuthenticationTest.java), [UserApiTest](../src/test/java/roombook/accounts/web/api/UserApiTest.java) |
| SEC-05 | [AuthenticationTest](../src/test/java/roombook/accounts/security/AuthenticationTest.java) |
| SEC-06 | [AuthenticationTest](../src/test/java/roombook/accounts/security/AuthenticationTest.java) |
| SEC-07 | [AuthenticationTest](../src/test/java/roombook/accounts/security/AuthenticationTest.java) |
| SEC-08 | [AuthenticationTest](../src/test/java/roombook/accounts/security/AuthenticationTest.java), [CurrentUserFilterTest](../src/test/java/roombook/accounts/security/CurrentUserFilterTest.java) |
| SEC-09 | [AuthenticationTest](../src/test/java/roombook/accounts/security/AuthenticationTest.java) |
| SEC-10 | [AuthenticationTest](../src/test/java/roombook/accounts/security/AuthenticationTest.java) |
| SEC-11 | [AuthenticationTest](../src/test/java/roombook/accounts/security/AuthenticationTest.java), [InvitationHttpTest](../src/test/java/roombook/accounts/web/InvitationHttpTest.java), [SecurityErrorHandlerTest](../src/test/java/roombook/platform/SecurityErrorHandlerTest.java) |
| SEC-12 | [AuthenticationTest](../src/test/java/roombook/accounts/security/AuthenticationTest.java), [UiShellTest](../src/test/java/roombook/scheduling/web/ui/UiShellTest.java) |
| SEC-13 | [AuthenticationTest](../src/test/java/roombook/accounts/security/AuthenticationTest.java) |
| SEC-14 | [AuthenticationTest](../src/test/java/roombook/accounts/security/AuthenticationTest.java), [SecurityErrorHandlerTest](../src/test/java/roombook/platform/SecurityErrorHandlerTest.java), [UiShellTest](../src/test/java/roombook/scheduling/web/ui/UiShellTest.java) |
| SEC-15 | [AuthenticationTest](../src/test/java/roombook/accounts/security/AuthenticationTest.java) |
| SEC-16 | [ProductionConfigurationTest](../src/test/java/roombook/platform/ProductionConfigurationTest.java) |
| OPS-01 | [ApiExceptionHandlerTest](../src/test/java/roombook/platform/ApiExceptionHandlerTest.java), [ConstraintNamesTest](../src/test/java/roombook/platform/ConstraintNamesTest.java), [ObservabilityHttpTest](../src/test/java/roombook/platform/ObservabilityHttpTest.java), [BookingApiTest](../src/test/java/roombook/scheduling/web/api/BookingApiTest.java) |
| OPS-02 | [ApiExceptionHandlerTest](../src/test/java/roombook/platform/ApiExceptionHandlerTest.java) |
| OPS-03 | [ProductionLoggingTest](../src/test/java/roombook/platform/ProductionLoggingTest.java), [RequestIdFilterTest](../src/test/java/roombook/platform/RequestIdFilterTest.java), [RequestLoggingFilterTest](../src/test/java/roombook/platform/RequestLoggingFilterTest.java), [UiShellTest](../src/test/java/roombook/scheduling/web/ui/UiShellTest.java) |
| OPS-04 | [ObservabilityHttpTest](../src/test/java/roombook/platform/ObservabilityHttpTest.java) |
| OPS-05 | [AccountServiceTest](../src/test/java/roombook/accounts/application/AccountServiceTest.java), [ObservabilityHttpTest](../src/test/java/roombook/platform/ObservabilityHttpTest.java), [BookingServiceTest](../src/test/java/roombook/scheduling/application/BookingServiceTest.java), [RoomServiceTest](../src/test/java/roombook/scheduling/application/RoomServiceTest.java) |
| OPS-06 | [ObservabilityHttpTest](../src/test/java/roombook/platform/ObservabilityHttpTest.java) |
| OPS-09 | [ClockConfigurationTest](../src/test/java/roombook/platform/ClockConfigurationTest.java), [ProductionConfigurationTest](../src/test/java/roombook/platform/ProductionConfigurationTest.java), [BookingRulesTest](../src/test/java/roombook/scheduling/domain/BookingRulesTest.java), [RoomTimelineTest](../src/test/java/roombook/scheduling/web/ui/RoomTimelineTest.java) |
| OPS-10 | [DatabaseSchemaMigrationTest](../src/test/java/roombook/testsupport/DatabaseSchemaMigrationTest.java), [SmokeEnvironmentTest](../src/test/java/roombook/testsupport/SmokeEnvironmentTest.java) |
| OPS-13 | [InvariantAuditTest](../src/test/java/roombook/testsupport/InvariantAuditTest.java) |
| TS-01 | [BookingTest](../src/test/java/roombook/scheduling/domain/BookingTest.java), [BookingTimePolicyTest](../src/test/java/roombook/scheduling/domain/BookingTimePolicyTest.java) |
| TS-02 | [BookingTest](../src/test/java/roombook/scheduling/domain/BookingTest.java) |
| TS-03 | [AdminPolicyTest](../src/test/java/roombook/accounts/domain/AdminPolicyTest.java), [AppUserTest](../src/test/java/roombook/accounts/domain/AppUserTest.java), [PasswordPolicyTest](../src/test/java/roombook/accounts/domain/PasswordPolicyTest.java), [RoomTest](../src/test/java/roombook/scheduling/domain/RoomTest.java) |
| TS-04 | [ComparisonKeyTest](../src/test/java/roombook/kernel/ComparisonKeyTest.java) |
| TS-05 | [OfficeClockTest](../src/test/java/roombook/kernel/OfficeClockTest.java), [SchedulingConcurrencyTest](../src/test/java/roombook/scheduling/application/SchedulingConcurrencyTest.java), [BookingTest](../src/test/java/roombook/scheduling/domain/BookingTest.java), [BookingTimePolicyTest](../src/test/java/roombook/scheduling/domain/BookingTimePolicyTest.java) |
| TS-06 | [AccountQueriesTest](../src/test/java/roombook/accounts/application/AccountQueriesTest.java), [AccountServiceTest](../src/test/java/roombook/accounts/application/AccountServiceTest.java), [BookingQueriesTest](../src/test/java/roombook/scheduling/application/BookingQueriesTest.java), [BookingServiceTest](../src/test/java/roombook/scheduling/application/BookingServiceTest.java), [OccupancyQueriesTest](../src/test/java/roombook/scheduling/application/OccupancyQueriesTest.java), [RoomServiceTest](../src/test/java/roombook/scheduling/application/RoomServiceTest.java) |
| TS-07 | [ArchitectureTest](../src/test/java/roombook/ArchitectureTest.java) |
| TS-08 | [UserApiTest](../src/test/java/roombook/accounts/web/api/UserApiTest.java), [BookingApiTest](../src/test/java/roombook/scheduling/web/api/BookingApiTest.java), [RoomApiTest](../src/test/java/roombook/scheduling/web/api/RoomApiTest.java) |
| TS-09 | [UserApiTest](../src/test/java/roombook/accounts/web/api/UserApiTest.java), [ApiExceptionHandlerTest](../src/test/java/roombook/platform/ApiExceptionHandlerTest.java), [ErrorCatalogTest](../src/test/java/roombook/platform/ErrorCatalogTest.java), [BookingApiTest](../src/test/java/roombook/scheduling/web/api/BookingApiTest.java), [RoomApiTest](../src/test/java/roombook/scheduling/web/api/RoomApiTest.java) |
| TS-10 | [AuthenticationTest](../src/test/java/roombook/accounts/security/AuthenticationTest.java), [InvitationHttpTest](../src/test/java/roombook/accounts/web/InvitationHttpTest.java) |
| TS-11 | [HorizontalTimelineTest](../src/test/java/roombook/scheduling/web/ui/HorizontalTimelineTest.java), [RoomTimelineTest](../src/test/java/roombook/scheduling/web/ui/RoomTimelineTest.java), [TimelineRenderingTest](../src/test/java/roombook/scheduling/web/ui/TimelineRenderingTest.java), [UiShellTest](../src/test/java/roombook/scheduling/web/ui/UiShellTest.java) |
| TS-12 | [UiShellTest](../src/test/java/roombook/scheduling/web/ui/UiShellTest.java) |
| TS-13 | [DatabaseSchemaMigrationTest](../src/test/java/roombook/testsupport/DatabaseSchemaMigrationTest.java) |
| TS-14 | [DatabaseSchemaMigrationTest](../src/test/java/roombook/testsupport/DatabaseSchemaMigrationTest.java) |
| TS-15 | [AccountsPersistenceTest](../src/test/java/roombook/accounts/persistence/AccountsPersistenceTest.java), [SchedulingQueriesTest](../src/test/java/roombook/scheduling/application/SchedulingQueriesTest.java), [SchedulingPersistenceTest](../src/test/java/roombook/scheduling/persistence/SchedulingPersistenceTest.java) |
| TS-16 | [ConcurrencyRequirementsTest](../src/test/java/roombook/acceptance/ConcurrencyRequirementsTest.java), [SecurityRequirementsTest](../src/test/java/roombook/acceptance/SecurityRequirementsTest.java), [TimeRulesTest](../src/test/java/roombook/acceptance/TimeRulesTest.java), [UC01AuthenticationTest](../src/test/java/roombook/acceptance/UC01AuthenticationTest.java), [UC02CreateBookingTest](../src/test/java/roombook/acceptance/UC02CreateBookingTest.java), [UC03CancelOwnBookingTest](../src/test/java/roombook/acceptance/UC03CancelOwnBookingTest.java), [UC04AdminCancelBookingTest](../src/test/java/roombook/acceptance/UC04AdminCancelBookingTest.java), [UC05ManageRoomsTest](../src/test/java/roombook/acceptance/UC05ManageRoomsTest.java), [UC06ManageUsersTest](../src/test/java/roombook/acceptance/UC06ManageUsersTest.java), [ViewRequirementsTest](../src/test/java/roombook/acceptance/ViewRequirementsTest.java) |
| TS-17 | [AccountAuthenticationProviderTest](../src/test/java/roombook/accounts/security/AccountAuthenticationProviderTest.java), [AuthenticationTest](../src/test/java/roombook/accounts/security/AuthenticationTest.java), [CurrentUserFilterTest](../src/test/java/roombook/accounts/security/CurrentUserFilterTest.java) |
| TS-18 | [SchedulingConcurrencyTest](../src/test/java/roombook/scheduling/application/SchedulingConcurrencyTest.java) |
| TS-19 | [AccountConcurrencyTest](../src/test/java/roombook/accounts/application/AccountConcurrencyTest.java), [SchedulingConcurrencyTest](../src/test/java/roombook/scheduling/application/SchedulingConcurrencyTest.java) |
| TS-20 | [ObservabilityHttpTest](../src/test/java/roombook/platform/ObservabilityHttpTest.java), [ProductionConfigurationTest](../src/test/java/roombook/platform/ProductionConfigurationTest.java) |
| TS-21 | [ObservabilityHttpTest](../src/test/java/roombook/platform/ObservabilityHttpTest.java) |
| TS-27 | [UiStyleTest](../src/test/java/roombook/UiStyleTest.java) |
| CS-01 | [SchedulingConcurrencyTest](../src/test/java/roombook/scheduling/application/SchedulingConcurrencyTest.java), [BookingApiTest](../src/test/java/roombook/scheduling/web/api/BookingApiTest.java), [InterleavingUtilityTest](../src/test/java/roombook/testsupport/InterleavingUtilityTest.java) |
| CS-02 | [SchedulingConcurrencyTest](../src/test/java/roombook/scheduling/application/SchedulingConcurrencyTest.java) |
| CS-03 | [SchedulingConcurrencyTest](../src/test/java/roombook/scheduling/application/SchedulingConcurrencyTest.java) |
| CS-04 | [SchedulingConcurrencyTest](../src/test/java/roombook/scheduling/application/SchedulingConcurrencyTest.java) |
| CS-05 | [SchedulingConcurrencyTest](../src/test/java/roombook/scheduling/application/SchedulingConcurrencyTest.java) |
| CS-06 | [SchedulingConcurrencyTest](../src/test/java/roombook/scheduling/application/SchedulingConcurrencyTest.java) |
| CS-07 | [SchedulingConcurrencyTest](../src/test/java/roombook/scheduling/application/SchedulingConcurrencyTest.java) |
| CS-08 | [SchedulingConcurrencyTest](../src/test/java/roombook/scheduling/application/SchedulingConcurrencyTest.java) |
| CS-09 | [AccountConcurrencyTest](../src/test/java/roombook/accounts/application/AccountConcurrencyTest.java) |
| CS-10 | [AccountConcurrencyTest](../src/test/java/roombook/accounts/application/AccountConcurrencyTest.java) |
| CS-11 | [AccountConcurrencyTest](../src/test/java/roombook/accounts/application/AccountConcurrencyTest.java), [SchedulingConcurrencyTest](../src/test/java/roombook/scheduling/application/SchedulingConcurrencyTest.java) |
| CS-12 | [SchedulingConcurrencyTest](../src/test/java/roombook/scheduling/application/SchedulingConcurrencyTest.java) |
| CS-13 | [SchedulingConcurrencyTest](../src/test/java/roombook/scheduling/application/SchedulingConcurrencyTest.java) |
| CS-14 | [SchedulingConcurrencyTest](../src/test/java/roombook/scheduling/application/SchedulingConcurrencyTest.java), [BookingApiTest](../src/test/java/roombook/scheduling/web/api/BookingApiTest.java) |
| A-06 | [RoomTest](../src/test/java/roombook/scheduling/domain/RoomTest.java) |
| A-14 | [AppUserTest](../src/test/java/roombook/accounts/domain/AppUserTest.java), [RoomTest](../src/test/java/roombook/scheduling/domain/RoomTest.java), [RoomApiTest](../src/test/java/roombook/scheduling/web/api/RoomApiTest.java) |
| A-15 | [AppUserTest](../src/test/java/roombook/accounts/domain/AppUserTest.java), [ComparisonKeyTest](../src/test/java/roombook/kernel/ComparisonKeyTest.java), [RoomTest](../src/test/java/roombook/scheduling/domain/RoomTest.java) |
| A-16 | [InvariantAuditTest](../src/test/java/roombook/testsupport/InvariantAuditTest.java) |
| A-17 | [AccountServiceTest](../src/test/java/roombook/accounts/application/AccountServiceTest.java), [AdminPolicyTest](../src/test/java/roombook/accounts/domain/AdminPolicyTest.java), [AppUserTest](../src/test/java/roombook/accounts/domain/AppUserTest.java), [ActorTest](../src/test/java/roombook/kernel/ActorTest.java), [BookingServiceTest](../src/test/java/roombook/scheduling/application/BookingServiceTest.java), [RoomServiceTest](../src/test/java/roombook/scheduling/application/RoomServiceTest.java), [RoomTest](../src/test/java/roombook/scheduling/domain/RoomTest.java) |
| A-19 | [AdminBootstrapTest](../src/test/java/roombook/accounts/application/AdminBootstrapTest.java), [AdminPolicyTest](../src/test/java/roombook/accounts/domain/AdminPolicyTest.java) |
| A-23 | [DatabaseSchemaMigrationTest](../src/test/java/roombook/testsupport/DatabaseSchemaMigrationTest.java) |
| A-24 | [DatabaseSchemaMigrationTest](../src/test/java/roombook/testsupport/DatabaseSchemaMigrationTest.java) |
| A-25 | [RoomTest](../src/test/java/roombook/scheduling/domain/RoomTest.java) |
| A-30 | [ActorTest](../src/test/java/roombook/kernel/ActorTest.java), [DomainExceptionTest](../src/test/java/roombook/kernel/DomainExceptionTest.java), [EnumsVerificationTest](../src/test/java/roombook/kernel/EnumsVerificationTest.java) |
| A-31 | [UserDirectoryQueriesTest](../src/test/java/roombook/accounts/application/UserDirectoryQueriesTest.java), [BookingQueriesTest](../src/test/java/roombook/scheduling/application/BookingQueriesTest.java), [OccupancyQueriesTest](../src/test/java/roombook/scheduling/application/OccupancyQueriesTest.java), [SchedulingQueriesTest](../src/test/java/roombook/scheduling/application/SchedulingQueriesTest.java) |
| A-32 | [OccupancyQueriesTest](../src/test/java/roombook/scheduling/application/OccupancyQueriesTest.java), [SchedulingQueriesTest](../src/test/java/roombook/scheduling/application/SchedulingQueriesTest.java), [RoomApiTest](../src/test/java/roombook/scheduling/web/api/RoomApiTest.java) |
| A-33 | [ActorTest](../src/test/java/roombook/kernel/ActorTest.java), [DomainExceptionTest](../src/test/java/roombook/kernel/DomainExceptionTest.java), [EnumsVerificationTest](../src/test/java/roombook/kernel/EnumsVerificationTest.java) |
| A-34 | [AccountQueriesTest](../src/test/java/roombook/accounts/application/AccountQueriesTest.java), [UserDirectoryQueriesTest](../src/test/java/roombook/accounts/application/UserDirectoryQueriesTest.java), [SchedulingQueriesTest](../src/test/java/roombook/scheduling/application/SchedulingQueriesTest.java) |
| A-36 | [TextRulesTest](../src/test/java/roombook/kernel/TextRulesTest.java), [RoomTest](../src/test/java/roombook/scheduling/domain/RoomTest.java), [RoomApiTest](../src/test/java/roombook/scheduling/web/api/RoomApiTest.java) |
| A-37 | [RoomApiTest](../src/test/java/roombook/scheduling/web/api/RoomApiTest.java) |
| A-38 | [OccupancyQueriesTest](../src/test/java/roombook/scheduling/application/OccupancyQueriesTest.java) |
| A-42 | [PasswordPolicyTest](../src/test/java/roombook/accounts/domain/PasswordPolicyTest.java) |
| A-43 | [InvariantAuditTest](../src/test/java/roombook/testsupport/InvariantAuditTest.java) |
| A-48 | [OccupancyQueriesTest](../src/test/java/roombook/scheduling/application/OccupancyQueriesTest.java), [SchedulingQueriesTest](../src/test/java/roombook/scheduling/application/SchedulingQueriesTest.java) |
| A-53 | [TestConventionsTest](../src/test/java/roombook/testsupport/TestConventionsTest.java) |
| A-55 | [TextRulesTest](../src/test/java/roombook/kernel/TextRulesTest.java) |
| A-56 | [DomainExceptionTest](../src/test/java/roombook/kernel/DomainExceptionTest.java), [EnumsVerificationTest](../src/test/java/roombook/kernel/EnumsVerificationTest.java), [ErrorCatalogTest](../src/test/java/roombook/platform/ErrorCatalogTest.java) |
| A-65 | [RoomTimelineTest](../src/test/java/roombook/scheduling/web/ui/RoomTimelineTest.java) |
| A-67 | [EmployeeUiTest](../src/test/java/roombook/scheduling/web/ui/EmployeeUiTest.java) |
| A-68 | [UiShellTest](../src/test/java/roombook/scheduling/web/ui/UiShellTest.java) |
| A-72 | [EmployeeUiTest](../src/test/java/roombook/scheduling/web/ui/EmployeeUiTest.java) |
| D-01 | [BookingIntervalTest](../src/test/java/roombook/scheduling/domain/BookingIntervalTest.java) |
| D-03 | [DatabaseSchemaMigrationTest](../src/test/java/roombook/testsupport/DatabaseSchemaMigrationTest.java) |
| D-04 | [AccountConcurrencyTest](../src/test/java/roombook/accounts/application/AccountConcurrencyTest.java), [AccountServiceTest](../src/test/java/roombook/accounts/application/AccountServiceTest.java), [AccountsPersistenceTest](../src/test/java/roombook/accounts/persistence/AccountsPersistenceTest.java), [BookingServiceTest](../src/test/java/roombook/scheduling/application/BookingServiceTest.java), [RoomServiceTest](../src/test/java/roombook/scheduling/application/RoomServiceTest.java), [SchedulingConcurrencyTest](../src/test/java/roombook/scheduling/application/SchedulingConcurrencyTest.java), [SchedulingPersistenceTest](../src/test/java/roombook/scheduling/persistence/SchedulingPersistenceTest.java) |
| D-05 | [DatabaseSchemaMigrationTest](../src/test/java/roombook/testsupport/DatabaseSchemaMigrationTest.java) |
| D-09 | [AccountsPersistenceTest](../src/test/java/roombook/accounts/persistence/AccountsPersistenceTest.java), [SchedulingPersistenceTest](../src/test/java/roombook/scheduling/persistence/SchedulingPersistenceTest.java) |
| D-10 | [AccountServiceTest](../src/test/java/roombook/accounts/application/AccountServiceTest.java), [BookingServiceTest](../src/test/java/roombook/scheduling/application/BookingServiceTest.java), [RoomServiceTest](../src/test/java/roombook/scheduling/application/RoomServiceTest.java) |
| D-12 | [BookingQueriesTest](../src/test/java/roombook/scheduling/application/BookingQueriesTest.java), [SchedulingQueriesTest](../src/test/java/roombook/scheduling/application/SchedulingQueriesTest.java) |
| D-19 | [ProductionLoggingTest](../src/test/java/roombook/platform/ProductionLoggingTest.java) |
| D-20 | [DatabaseSchemaMigrationTest](../src/test/java/roombook/testsupport/DatabaseSchemaMigrationTest.java) |
| D-22 | [AdminBootstrapTest](../src/test/java/roombook/accounts/application/AdminBootstrapTest.java) |
| D-24 | [SchedulingConcurrencyTest](../src/test/java/roombook/scheduling/application/SchedulingConcurrencyTest.java) |
| D-29 | [EmployeeUiTest](../src/test/java/roombook/scheduling/web/ui/EmployeeUiTest.java) |
| R-17 | [TimelineRenderingTest](../src/test/java/roombook/scheduling/web/ui/TimelineRenderingTest.java) |
| R-18 | [RoomTimelineTest](../src/test/java/roombook/scheduling/web/ui/RoomTimelineTest.java) |
| EP-01 | [RoomApiTest](../src/test/java/roombook/scheduling/web/api/RoomApiTest.java) |
| EP-02 | [RoomApiTest](../src/test/java/roombook/scheduling/web/api/RoomApiTest.java) |
| EP-03 | [RoomApiTest](../src/test/java/roombook/scheduling/web/api/RoomApiTest.java) |
| EP-04 | [RoomApiTest](../src/test/java/roombook/scheduling/web/api/RoomApiTest.java) |
| EP-05 | [RoomApiTest](../src/test/java/roombook/scheduling/web/api/RoomApiTest.java) |
| EP-06 | [RoomApiTest](../src/test/java/roombook/scheduling/web/api/RoomApiTest.java) |
| EP-07 | [RoomApiTest](../src/test/java/roombook/scheduling/web/api/RoomApiTest.java) |
| EP-08 | [BookingApiTest](../src/test/java/roombook/scheduling/web/api/BookingApiTest.java) |
| EP-09 | [BookingApiTest](../src/test/java/roombook/scheduling/web/api/BookingApiTest.java) |
| EP-10 | [BookingApiTest](../src/test/java/roombook/scheduling/web/api/BookingApiTest.java) |
| EP-11 | [BookingApiTest](../src/test/java/roombook/scheduling/web/api/BookingApiTest.java) |
| EP-12 | [BookingApiTest](../src/test/java/roombook/scheduling/web/api/BookingApiTest.java) |
| EP-13 | [UserApiTest](../src/test/java/roombook/accounts/web/api/UserApiTest.java) |
| EP-14 | [UserApiTest](../src/test/java/roombook/accounts/web/api/UserApiTest.java) |
| EP-15 | [UserApiTest](../src/test/java/roombook/accounts/web/api/UserApiTest.java) |
| EP-16 | [UserApiTest](../src/test/java/roombook/accounts/web/api/UserApiTest.java) |
| EP-17 | [UserApiTest](../src/test/java/roombook/accounts/web/api/UserApiTest.java) |
| EP-18 | [UserApiTest](../src/test/java/roombook/accounts/web/api/UserApiTest.java) |
| EP-19 | [UserApiTest](../src/test/java/roombook/accounts/web/api/UserApiTest.java) |
| EP-20 | [AuthenticationTest](../src/test/java/roombook/accounts/security/AuthenticationTest.java) |
| PG-01 | [EmployeeUiTest](../src/test/java/roombook/scheduling/web/ui/EmployeeUiTest.java) |
| PG-02 | [EmployeeUiTest](../src/test/java/roombook/scheduling/web/ui/EmployeeUiTest.java) |
| PG-03 | [EmployeeUiTest](../src/test/java/roombook/scheduling/web/ui/EmployeeUiTest.java) |
| PG-04 | [EmployeeUiTest](../src/test/java/roombook/scheduling/web/ui/EmployeeUiTest.java) |
| PG-05 | [EmployeeUiTest](../src/test/java/roombook/scheduling/web/ui/EmployeeUiTest.java) |
| PG-06 | [AdminUiTest](../src/test/java/roombook/scheduling/web/ui/AdminUiTest.java) |
| PG-07 | [AdminUiTest](../src/test/java/roombook/scheduling/web/ui/AdminUiTest.java) |
| PG-08 | [AdminUiTest](../src/test/java/roombook/scheduling/web/ui/AdminUiTest.java) |
| PG-09 | [AdminUiTest](../src/test/java/roombook/scheduling/web/ui/AdminUiTest.java) |
| PG-10 | [AdminUiTest](../src/test/java/roombook/scheduling/web/ui/AdminUiTest.java) |
| PG-11 | [AdminUiTest](../src/test/java/roombook/scheduling/web/ui/AdminUiTest.java) |
| PG-12 | [AdminUiTest](../src/test/java/roombook/scheduling/web/ui/AdminUiTest.java) |
| PG-13 | [UiShellTest](../src/test/java/roombook/scheduling/web/ui/UiShellTest.java) |
| HX-01 | [EmployeeUiTest](../src/test/java/roombook/scheduling/web/ui/EmployeeUiTest.java) |
| HX-02 | [EmployeeUiTest](../src/test/java/roombook/scheduling/web/ui/EmployeeUiTest.java) |
| HX-03 | [EmployeeUiTest](../src/test/java/roombook/scheduling/web/ui/EmployeeUiTest.java) |
| HX-04 | [AdminUiTest](../src/test/java/roombook/scheduling/web/ui/AdminUiTest.java) |
| HX-05 | [AdminUiTest](../src/test/java/roombook/scheduling/web/ui/AdminUiTest.java) |
| HX-06 | [AdminUiTest](../src/test/java/roombook/scheduling/web/ui/AdminUiTest.java) |
| HX-07 | [AdminUiTest](../src/test/java/roombook/scheduling/web/ui/AdminUiTest.java) |
| HX-08 | [AdminUiTest](../src/test/java/roombook/scheduling/web/ui/AdminUiTest.java) |
| HX-09 | [AdminUiTest](../src/test/java/roombook/scheduling/web/ui/AdminUiTest.java) |
| HX-10 | [UiShellTest](../src/test/java/roombook/scheduling/web/ui/UiShellTest.java) |
| UI-01 | [UiStyleTest](../src/test/java/roombook/UiStyleTest.java) |
| UI-02 | [UiStyleTest](../src/test/java/roombook/UiStyleTest.java) |
| UI-04 | [UiStyleTest](../src/test/java/roombook/UiStyleTest.java) |
| UI-05 | [UiStyleTest](../src/test/java/roombook/UiStyleTest.java), [TimelineRenderingTest](../src/test/java/roombook/scheduling/web/ui/TimelineRenderingTest.java) |
| UI-06 | [UiStyleTest](../src/test/java/roombook/UiStyleTest.java) |
| UI-07 | [UiStyleTest](../src/test/java/roombook/UiStyleTest.java) |
| UI-09 | [UiStyleTest](../src/test/java/roombook/UiStyleTest.java) |
| UI-12 | [HorizontalTimelineTest](../src/test/java/roombook/scheduling/web/ui/HorizontalTimelineTest.java), [RoomTimelineTest](../src/test/java/roombook/scheduling/web/ui/RoomTimelineTest.java), [TimelineRenderingTest](../src/test/java/roombook/scheduling/web/ui/TimelineRenderingTest.java), [UiShellTest](../src/test/java/roombook/scheduling/web/ui/UiShellTest.java) |
| UC-01 | [UC01AuthenticationTest](../src/test/java/roombook/acceptance/UC01AuthenticationTest.java) |
| UC-02 | [UC02CreateBookingTest](../src/test/java/roombook/acceptance/UC02CreateBookingTest.java) |
| UC-03 | [UC03CancelOwnBookingTest](../src/test/java/roombook/acceptance/UC03CancelOwnBookingTest.java) |
| UC-04 | [UC04AdminCancelBookingTest](../src/test/java/roombook/acceptance/UC04AdminCancelBookingTest.java) |
| UC-05 | [UC05ManageRoomsTest](../src/test/java/roombook/acceptance/UC05ManageRoomsTest.java) |
| UC-06 | [UC06ManageUsersTest](../src/test/java/roombook/acceptance/UC06ManageUsersTest.java) |
