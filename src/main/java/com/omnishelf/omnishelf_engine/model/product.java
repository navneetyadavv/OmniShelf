// Product.java
@Entity
@Table(name = "products")
@Data
@NoArgsConstructor
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String brand;           // e.g. "Nike"

    @Column(nullable = false)
    private String name;            // e.g. "Air Max"

    @Column(nullable = false)
    private String category;        // e.g. "Footwear"

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}