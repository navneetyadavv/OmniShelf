// ProductVariant.java  — this is the actual SKU
@Entity
@Table(name = "product_variants")
@Data
@NoArgsConstructor
public class ProductVariant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    private String color;           // "Blue"
    private String size;            // "8" or "128GB"

    @Column(nullable = false)
    private BigDecimal price;

    @Column(nullable = false)
    private Integer stockQuantity;  // current stock count

    @Column(unique = true)
    private String sku;             // auto-generated: NIKE-AIRMAX-BLUE-8
}