package com.inventory.service;

import com.inventory.entity.Category;
import com.inventory.entity.Product;
import com.inventory.repository.CategoryRepository;
import com.inventory.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    @Autowired
    public ProductService(ProductRepository productRepository, CategoryRepository categoryRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
    }

    public Product createProduct(Product product, Long categoryId) {
        validateProduct(product);
        if (productRepository.existsBySku(product.getSku())) {
            throw new IllegalArgumentException("Product with this SKU already exists");
        }

        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new IllegalArgumentException("Category not found"));

        if (!Boolean.TRUE.equals(category.getIsActive())) {
            throw new IllegalArgumentException("Category must be active");
        }
        product.setId(null);
        product.setVersion(0);
        product.setCurrentQuantity(0);
        product.setIsActive(true);
        product.setCategory(category);
        return productRepository.save(product);
    }

    public List<Product> getAllActiveProducts() {
        return productRepository.findByIsActiveTrue();
    }

    public List<Product> getProductsByCategory(Long categoryId) {
        return productRepository.findByCategoryId(categoryId);
    }

    public Product getProductById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));
    }

    public Product updateProduct(Long id, Product updatedData, Long categoryId) {
        validateProductDetails(updatedData);
        Product existing = getProductById(id);

        existing.setName(updatedData.getName());
        existing.setUnit(updatedData.getUnit());
        existing.setMinStockThreshold(updatedData.getMinStockThreshold());
        existing.setBarcode(updatedData.getBarcode());
        existing.setImagePath(updatedData.getImagePath());

        if (categoryId != null) {
            Category category = categoryRepository.findById(categoryId)
                    .orElseThrow(() -> new IllegalArgumentException("Category not found"));
            if (!Boolean.TRUE.equals(category.getIsActive())) {
                throw new IllegalArgumentException("Category must be active");
            }
            existing.setCategory(category);
        }

        return productRepository.save(existing);
    }

    public void deactivateProduct(Long id) {
        Product existing = getProductById(id);
        if (existing.getCurrentQuantity() != 0) {
            throw new IllegalArgumentException("Clear stock before deactivating a product");
        }
        existing.setIsActive(false);
        productRepository.save(existing);
    }

    public java.util.List<Product> getLowStockProducts() {
        return productRepository.findByIsActiveTrue().stream()
                .filter(p -> p.getCurrentQuantity() < p.getMinStockThreshold())
                .collect(java.util.stream.Collectors.toList());
    }

    public Product getProductByBarcode(String barcode) {
        return productRepository.findByBarcode(barcode)
                .orElseThrow(() -> new IllegalArgumentException("Product not found for barcode: " + barcode));
    }

    private void validateProduct(Product product) {
        validateProductDetails(product);
        if (product.getSku() == null || product.getSku().isBlank() || product.getSku().length() > 50) {
            throw new IllegalArgumentException("SKU must contain 1 to 50 characters");
        }
        product.setSku(product.getSku().trim());
    }

    private void validateProductDetails(Product product) {
        if (product == null || product.getName() == null || product.getName().isBlank() || product.getName().length() > 150 ||
                product.getUnit() == null || product.getUnit().isBlank() || product.getUnit().length() > 20 ||
                product.getMinStockThreshold() == null || product.getMinStockThreshold() < 0) {
            throw new IllegalArgumentException("Product needs a name, unit and non-negative minimum stock threshold");
        }
        if (product.getBarcode() != null && product.getBarcode().length() > 50) {
            throw new IllegalArgumentException("Barcode must be at most 50 characters");
        }
        product.setName(product.getName().trim());
        product.setUnit(product.getUnit().trim());
        if (product.getBarcode() != null) {
            product.setBarcode(product.getBarcode().isBlank() ? null : product.getBarcode().trim());
        }
    }
}
