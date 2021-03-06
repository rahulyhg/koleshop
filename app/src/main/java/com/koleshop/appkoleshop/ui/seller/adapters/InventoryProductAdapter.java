package com.koleshop.appkoleshop.ui.seller.adapters;

import android.content.Context;
import android.content.Intent;
import android.os.Parcelable;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.koleshop.appkoleshop.constant.Constants;
import com.koleshop.appkoleshop.listeners.InventoryProductClickListener;
import com.koleshop.appkoleshop.model.ProductSelectionRequest;
import com.koleshop.appkoleshop.model.parcel.EditProduct;
import com.koleshop.appkoleshop.model.parcel.SellerSettings;
import com.koleshop.appkoleshop.model.realm.Product;
import com.koleshop.appkoleshop.model.realm.ProductVariety;
import com.koleshop.appkoleshop.services.CommonIntentService;
import com.koleshop.appkoleshop.ui.seller.activities.ProductActivity;
import com.koleshop.appkoleshop.ui.seller.viewholders.InventoryProductViewHolder;
import com.koleshop.appkoleshop.util.CartUtils;
import com.koleshop.appkoleshop.util.CommonUtils;
import com.koleshop.appkoleshop.util.KoleCacheUtil;
import com.koleshop.appkoleshop.util.KoleshopUtils;
import com.koleshop.appkoleshop.util.ProductUtil;
import com.tonicartos.superslim.GridSLM;
import com.tonicartos.superslim.LayoutManager;
import com.tonicartos.superslim.LinearSLM;

import org.parceler.Parcels;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by Gundeep on 19/10/15.
 */
public class InventoryProductAdapter extends RecyclerView.Adapter<InventoryProductViewHolder> {

    private static final String TAG = "InventoryProductAdapter";

    private static final int VIEW_TYPE_HEADER = 0x01;

    private static final int VIEW_TYPE_CONTENT = 0x00;

    private static final int VIEW_TYPE_CONTENT_EXPANDED = 0x02;

    private LayoutInflater inflater;
    List<Product> products;
    Context context;
    List<LineItem> mItems;
    int expandedItemPosition, expandedItemPositionOld;
    List<String> pendingRequestsRandomIds;
    long categoryId;
    boolean myInventory;
    boolean customerView;
    private SellerSettings sellerSettings;


    public InventoryProductAdapter(Context context, long categoryId, boolean myInventory, boolean customerView) {
        this.context = context;
        this.myInventory = myInventory;
        this.categoryId = categoryId;
        this.customerView = customerView;
        inflater = LayoutInflater.from(context);
        pendingRequestsRandomIds = new ArrayList<>();
    }

    public void setProductsList(List<Product> productsList) {
        products = productsList;
        mItems = new ArrayList<>();

        //Insert headers into list of items.
        String lastHeader = "";
        int headerCount = 0;
        int sectionFirstPosition = 0;
        for (int i = 0; i < products.size(); i++) {
            String header = products.get(i).getBrand();
            if (!TextUtils.equals(lastHeader, header)) {
                // Insert new header view and update section data.
                sectionFirstPosition = i + headerCount;
                lastHeader = header;
                headerCount += 1;
                mItems.add(new LineItem(header, true, sectionFirstPosition, null));
            }
            mItems.add(new LineItem("", false, sectionFirstPosition, products.get(i)));
        }

        Log.d(TAG, "setting new product list with size " + productsList.size());

        //notifyDataSetChanged();
    }

    public void setSellerSettings(SellerSettings sellerSettings) {
        this.sellerSettings = sellerSettings;
    }

    //Item viewholder methods

    @Override
    public InventoryProductViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {

        View view;
        if (viewType == VIEW_TYPE_HEADER) {
            view = LayoutInflater.from(parent.getContext())
                    .inflate(com.koleshop.appkoleshop.R.layout.view_list_header, parent, false);
        } else if (viewType == VIEW_TYPE_CONTENT) {
            view = LayoutInflater.from(parent.getContext())
                    .inflate(com.koleshop.appkoleshop.R.layout.item_rv_inventory_product, parent, false);
        } else {
            view = LayoutInflater.from(parent.getContext())
                    .inflate(com.koleshop.appkoleshop.R.layout.item_rv_inventory_product_expanded, parent, false);
        }

        InventoryProductViewHolder holder = new InventoryProductViewHolder(view, viewType, context, myInventory, customerView, sellerSettings);
        Log.d(TAG, "on create view holder of type=" + viewType);
        return holder;
    }

    @Override
    public void onBindViewHolder(InventoryProductViewHolder holder, final int position) {

        LineItem item = mItems.get(position);
        final View itemView = holder.itemView;

        if (position == expandedItemPosition && position != 0) {
            holder.bindData(VIEW_TYPE_CONTENT_EXPANDED, null, item.product, item.varietyProgress, position);
        } else if (item.isHeader) {
            holder.bindData(VIEW_TYPE_HEADER, item.text, item.product, item.varietyProgress, position);
        } else {
            holder.bindData(VIEW_TYPE_CONTENT, item.text, item.product, item.varietyProgress, position);
        }
        holder.setClickListener(new InventoryProductClickListener() {
            @Override
            public void onItemClick(View v) {
                if (myInventory && !customerView) {
                    //open product edit screen
                    //Toast.makeText(context, "Product Edit screen is on its way!!", Toast.LENGTH_SHORT).show();
                    //@deprecated Intent editProductIntent = new Intent(context, ProductEditActivity.class);
                    Intent editProductIntent = new Intent(context, ProductActivity.class);
                    EditProduct pro = new EditProduct(mItems.get(position).product);
                    Parcelable parcelableProduct = Parcels.wrap(pro);
                    editProductIntent.putExtra("product", parcelableProduct);
                    context.startActivity(editProductIntent);
                } else if (!mItems.get(position).isHeader) {
                    //if the item is not a header, then
                    if (expandedItemPosition != position) {
                        expandItemAtPosition(position);
                    } else if (expandedItemPosition == position) {
                        collapseTheExpandedItem();
                    }
                }
            }

            @Override
            public boolean onItemLongClick(View v) {
                //do nothing
                return false;
            }
        });

        if (!mItems.get(position).isHeader && expandedItemPosition != position) {
            holder.setCheckBoxOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    for (ProductVariety ipv : mItems.get(position).product.getVarieties()) {
                        mItems.get(position).varietyProgress.put(ipv.getId(), true);
                    }
                    requestProductSelection(position, 0l, false);
                    notifyItemChanged(position);
                }
            });
        }

        //sticky header shit
        final GridSLM.LayoutParams lp = GridSLM.LayoutParams.from(itemView.getLayoutParams());
        // Overrides xml attrs, could use different layouts too.
        if (item.isHeader) {
            lp.headerDisplay = LayoutManager.LayoutParams.HEADER_STICKY | LayoutManager.LayoutParams.HEADER_INLINE;
            //Log.d("omgshit" , "" + (LayoutManager.LayoutParams.HEADER_STICKY | LayoutManager.LayoutParams.HEADER_INLINE));
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            lp.headerEndMarginIsAuto = true;
            lp.headerStartMarginIsAuto = true;
        }
        lp.setSlm(LinearSLM.ID);
        //lp.setColumnWidth(96);
        lp.setFirstPosition(item.sectionFirstPosition);
        itemView.setLayoutParams(lp);
        Log.d(TAG, "on bind view holder position " + position);
    }

    @Override
    public int getItemCount() {
        if (mItems != null) {
            //Log.d(TAG, "mItems.size()=" + mItems.size());
            return mItems.size();
        } else {
            return 0;
        }
    }

    public void requestProductSelection(int position, Long varietyId, boolean varietySelected) {
        Intent intent = new Intent(context, CommonIntentService.class);
        intent.setAction(Constants.ACTION_UPDATE_INVENTORY_PRODUCT_SELECTION);
        Product currentProduct = mItems.get(position).product;
        int productsSelected = ProductUtil.getProductSelectionCount(currentProduct);
        ArrayList<Long> productSelection = new ArrayList<>();
        ProductSelectionRequest request = new ProductSelectionRequest();
        if (varietyId == 0) {
            //all varieties selected
            for (ProductVariety ipv : currentProduct.getVarieties()) {
                productSelection.add(ipv.getId());
            }

            //product selection after success
            if (productsSelected == 0) {
                request.setWillSelectOnSuccess(true); //selection result - will select all the product varieties if the request is success
            } else {
                request.setWillSelectOnSuccess(false); // selection result - will deselect the products on request success
            }
        } else {
            mItems.get(position).varietyProgress.put(varietyId, true);
            productSelection.add(varietyId);
            request.setWillSelectOnSuccess(!varietySelected);
        }
        request.setProductVarietyIds(productSelection); // pv ids
        request.setPositionOfUpdate(position); //position of update
        String randomRequestId = CommonUtils.randomString(10);
        request.setRandomId(randomRequestId); //random id for request
        pendingRequestsRandomIds.add(randomRequestId);
        Parcelable wrappedRequest = Parcels.wrap(request);
        intent.putExtra("request", wrappedRequest);
        context.startService(intent);
    }

    public void increaseVarietyCount(int position, Long varietyId) {
        Product product = mItems.get(position).product;
        String title = product.getBrand() + " - " + product.getName();
        ProductVariety variety = getProductVarietyFromProductIndex(position, varietyId);
        if (variety != null) {
            CartUtils.increaseCount(title, variety, sellerSettings);
        }
    }

    public void decreaseVarietyCount(int position, Long varietyId) {
        ProductVariety variety = getProductVarietyFromProductIndex(position, varietyId);
        if (variety != null) {
            CartUtils.decreaseCount(variety, sellerSettings);
        }
    }

    private ProductVariety getProductVarietyFromProductIndex(int position, Long varietyId) {
        Product currentProduct = mItems.get(position).product;
        return KoleshopUtils.getProductVarietyFromProduct(currentProduct, varietyId);
    }

    private void expandItemAtPosition(int position) {
        expandedItemPositionOld = expandedItemPosition;
        expandedItemPosition = position;
        notifyItemChanged(expandedItemPosition);
        notifyItemChanged(expandedItemPositionOld);
    }

    public void collapseTheExpandedItem() {
        expandedItemPositionOld = expandedItemPosition;
        expandedItemPosition = 0;
        notifyItemChanged(expandedItemPosition);
        notifyItemChanged(expandedItemPositionOld);
    }

    @Override
    public int getItemViewType(int position) {
        if (position == expandedItemPosition && position != 0) {
            return VIEW_TYPE_CONTENT_EXPANDED;
        } else {
            return mItems.get(position).isHeader ? VIEW_TYPE_HEADER : VIEW_TYPE_CONTENT;
        }
    }

    public void updateProductsCache() {
        //product cache will be updated only when one or more than one product selection request completes in a category...
        //then on pause ,this function will be called, so that the cache is updated only once for a category (even if there are more that 1 product selection requests success)
        List<Product> products = new ArrayList<>();
        for (LineItem lineItem : mItems) {
            if (!lineItem.isHeader) {
                products.add(lineItem.product);
            }
        }
        //can only be called from my inventory = false
        if (products != null) {
            //complementary date is contained...because the products were loaded from cache
            KoleCacheUtil.cacheProductsListInRealm(products, true, true);
        }
    }

    private static class LineItem {

        public int sectionFirstPosition;

        public boolean isHeader;

        public String text;

        public Product product;

        public Map<Long, Boolean> varietyProgress;

        public LineItem(String text, boolean isHeader,
                        int sectionFirstPosition, Product product) {
            this.isHeader = isHeader;
            this.text = text;
            this.sectionFirstPosition = sectionFirstPosition;
            this.product = product;
            varietyProgress = new HashMap<>();
            if (product != null) {
                List<ProductVariety> listipv = product.getVarieties();
                for (ProductVariety ipv : listipv) {
                    varietyProgress.put(ipv.getId(), false);
                }
            }
        }
    }

    public List<String> getPendingRequestsRandomIds() {
        return pendingRequestsRandomIds;
    }

    public void updateProductSelection(ProductSelectionRequest request, boolean success) {
        int updatePosition = request.getPositionOfUpdate();

        LineItem lineItem = mItems.get(updatePosition);
        if (lineItem == null) {
            Toast.makeText(context, "line item is null at position " + updatePosition, Toast.LENGTH_SHORT).show();
        } else if (lineItem.product == null) {
            Toast.makeText(context, "product is null at position " + updatePosition, Toast.LENGTH_SHORT).show();
        } else if (lineItem.product.getVarieties() == null) {
            Toast.makeText(context, "varieties are null at position " + updatePosition, Toast.LENGTH_SHORT).show();
        } else {
            for (ProductVariety ipv : lineItem.product.getVarieties()) {
                if (request.getProductVarietyIds().contains(ipv.getId())) {
                    if (success) {
                        ipv.setVarietyValid(request.isWillSelectOnSuccess());
                    }
                    lineItem.varietyProgress.put(ipv.getId(), false);
                }
            }
        }
        notifyItemChanged(updatePosition);

        if (!success) {
            //todo make a snack bar with product and variety name
        }

        //remove this request from pending requests
        int randomIdLocation = pendingRequestsRandomIds.indexOf(request.getRandomId());
        pendingRequestsRandomIds.remove(randomIdLocation);
    }
}
