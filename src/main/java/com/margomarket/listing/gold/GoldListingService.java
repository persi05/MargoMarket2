package com.margomarket.listing.gold;

import com.margomarket.catalog.Catalog;
import com.margomarket.listing.internal.FavoriteRepository;
import com.margomarket.listing.Listing;
import com.margomarket.listing.internal.ListingRepository;
import com.margomarket.listing.internal.ListingStatusRepository;
import com.margomarket.moderation.ModerationScope;
import com.margomarket.moderation.UserModerationService;
import com.margomarket.shared.ForbiddenOperationException;
import com.margomarket.shared.NotFoundException;
import com.margomarket.user.User;

import jakarta.persistence.criteria.Predicate;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GoldListingService {

    private final UserModerationService userModeration;
    private final ListingRepository listings;
    private final Catalog catalog;
    private final ListingStatusRepository statuses;
    private final FavoriteRepository favorites;

    public Page<Listing> search(GoldListingFilter f) {
        if (
            (f.getMinAmount() != null &&
                f.getMaxAmount() != null &&
                f.getMinAmount() > f.getMaxAmount()) ||
            (f.getMinPrice() != null &&
                f.getMaxPrice() != null &&
                f.getMinPrice() > f.getMaxPrice())
        ) throw new IllegalArgumentException("Nieprawidłowy zakres");
        Sort sort = switch (f.getSort() == null || f.getSort().isBlank() ? "newest" : f.getSort()) {
            case "priceAsc" -> Sort.by("priceMinor").ascending();
            case "priceDesc" -> Sort.by("priceMinor").descending();
            case "amountAsc" -> Sort.by("goldAmount").ascending();
            case "amountDesc" -> Sort.by("goldAmount").descending();
            case "newest" -> Sort.by("createdAt").descending();
            default -> throw new IllegalArgumentException("Nieznane sortowanie");
        };
        Specification<Listing> spec = (root, q, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(root.get("listingKind"), "GOLD"));
            p.add(cb.equal(root.get("status").get("name"), "active"));
            if (f.getServerId() != null) p.add(
                cb.equal(root.get("server").get("id"), f.getServerId())
            );
            if (f.getMinAmount() != null) p.add(cb.ge(root.get("goldAmount"), f.getMinAmount()));
            if (f.getMaxAmount() != null) p.add(cb.le(root.get("goldAmount"), f.getMaxAmount()));
            if (f.getMinPrice() != null) p.add(cb.ge(root.get("priceMinor"), f.getMinPrice()));
            if (f.getMaxPrice() != null) p.add(cb.le(root.get("priceMinor"), f.getMaxPrice()));
            if (f.getSearch() != null && !f.getSearch().isBlank()) p.add(
                cb.like(
                    cb.lower(root.get("description")),
                    "%" +
                        f
                            .getSearch()
                            .trim()
                            .toLowerCase(Locale.ROOT)
                            .replace("!", "!!")
                            .replace("%", "!%")
                            .replace("_", "!_") +
                        "%",
                    '!'
                )
            );
            return cb.and(p.toArray(Predicate[]::new));
        };
        return listings.findAll(
            spec,
            PageRequest.of(f.getPage() - 1, 50, sort.and(Sort.by("id").descending()))
        );
    }

    @Transactional
    public Listing create(GoldListingRequest request, User owner) {
        userModeration.requireAllowed(owner,ModerationScope.GOLD_LISTINGS);
        if (!owner.isEmailVerified()) throw new ForbiddenOperationException("Zweryfikuj konto");
        Listing l = new Listing();
        l.setListingKind("GOLD");
        l.setUser(owner);
        l.setItemName("Złoto Margonem");
        l.setLevel(null);
        l.setItemType(null);
        l.setRarity(null);
        l.setItem(null);
        l.setCurrency(
            catalog
                .currencyNamed("PLN")
                .orElseThrow(() -> new NotFoundException("Brak waluty PLN"))
        );
        apply(l, request);
        l.setStatus(statuses.findByName("active").orElseThrow());
        return listings.save(l);
    }

    @Transactional
    public Listing update(Long id, GoldListingRequest request, User user) {
        userModeration.requireAllowed(user,ModerationScope.GOLD_LISTINGS);
        Listing l = owned(id, user);
        if (!l.isActive()) throw new IllegalArgumentException("Oferta nie jest aktywna");
        apply(l, request);
        return l;
    }

    @Transactional
    public Listing withdraw(Long id, User user) {
        Listing l = owned(id, user);
        if (!l.isActive()) throw new IllegalArgumentException("Oferta nie jest aktywna");
        l.setStatus(statuses.findByName("withdrawn").orElseThrow());
        favorites.deleteByListingId(id);
        return l;
    }

    private Listing owned(Long id, User user) {
        Listing l = listings
            .findByIdWithDetails(id)
            .orElseThrow(() -> new NotFoundException("Ogłoszenie nie istnieje"));
        if (
            !l.getUser().getId().equals(user.getId()) && !user.isAdmin()
        ) throw new ForbiddenOperationException("Brak uprawnień");
        if (!"GOLD".equals(l.getListingKind())) throw new IllegalArgumentException(
            "To nie jest oferta złota"
        );
        return l;
    }

    private void apply(Listing l, GoldListingRequest r) {
        l.setServer(
            catalog
                .server(r.serverId())
                .orElseThrow(() -> new NotFoundException("Świat nie istnieje"))
        );
        l.setGoldAmount(r.goldAmount());
        l.setPriceMinor(
            GoldPricing.total(r.goldAmount(), r.rateMode(), r.rateGoldUnits(), r.ratePriceMinor())
        );
        l.setGoldRateMode(r.rateMode());
        l.setRateGoldUnits(r.rateGoldUnits());
        l.setRatePriceMinor(r.ratePriceMinor());
        l.setDescription(r.description() == null ? null : r.description().trim());
        l.setContact(r.contact() == null ? "" : r.contact().trim());
    }
}
