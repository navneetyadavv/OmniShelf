// Bill.java
@Entity
@Table(name = "bills")
@Data
@NoArgsConstructor
public class Bill {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private String billNumber;          // human-readable: BILL-20240115-001
    private String customerName;
    private String shopkeeperPhone;     // WhatsApp number that created this bill

    @Enumerated(EnumType.STRING)
    private BillStatus status;          // DRAFT, CONFIRMED, CANCELLED

    private BigDecimal totalAmount;
    private BigDecimal taxAmount;
    private BigDecimal grandTotal;

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime confirmedAt;

    @OneToMany(mappedBy = "bill", cascade = CascadeType.ALL)
    private List<BillItem> items = new ArrayList<>();
}