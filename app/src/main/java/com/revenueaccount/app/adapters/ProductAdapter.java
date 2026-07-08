package com.revenueaccount.app.adapters;

import com.revenueaccount.app.utils.AppToast;

import android.content.Context;
import android.graphics.Color;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.revenueaccount.app.R;
import com.revenueaccount.app.api.ApiClient;
import java.util.HashMap;
import java.util.Map;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ProductAdapter extends RecyclerView.Adapter<ProductAdapter.VH> {

    public interface OnChanged { void refresh(); }

    private final Context ctx;
    private final OnChanged onChanged;
    private JsonArray data = new JsonArray();

    public ProductAdapter(Context ctx, OnChanged onChanged) {
        this.ctx = ctx;
        this.onChanged = onChanged;
    }

    public void setData(JsonArray d) { data = d != null ? d : new JsonArray(); notifyDataSetChanged(); }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup p, int v) {
        return new VH(LayoutInflater.from(ctx).inflate(R.layout.item_product, p, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        try {
            JsonObject prod = data.get(pos).getAsJsonObject();
            h.name.setText(str(prod, "name"));
            String brand = str(prod, "brand");
            h.meta.setText(str(prod, "category") + (brand.isEmpty() ? "" : " • " + brand));
            double purchase = prod.has("purchase_price") ? prod.get("purchase_price").getAsDouble() : 0;
            double sale = prod.has("sale_price") ? prod.get("sale_price").getAsDouble() : 0;
            h.price.setText("Kharid: ₹" + purchase + " | Bech: ₹" + sale);
            int qty = prod.has("quantity") ? prod.get("quantity").getAsInt() : 0;
            h.qty.setText(String.valueOf(qty));
            String stock = prod.has("stock_status") ? prod.get("stock_status").getAsString() : "IN";
            switch (stock) {
                case "IN": h.stock.setText("In Stock"); h.stock.setTextColor(Color.parseColor("#2A6B45")); break;
                case "LOW": h.stock.setText("Low Stock"); h.stock.setTextColor(Color.parseColor("#8F5416")); break;
                default: h.stock.setText("Out"); h.stock.setTextColor(Color.parseColor("#A83E3E"));
            }
            h.qty.setTextColor(h.stock.getCurrentTextColor());
            long id = prod.has("id") ? prod.get("id").getAsLong() : -1;
            h.btnAdd.setOnClickListener(v -> { if (id >= 0) addStockDialog(id); });
        } catch (Exception e) {
            android.util.Log.e("ProductAdapter", "Bind error at position " + pos, e);
        }
    }

    private String str(JsonObject o, String key) {
        return (o.has(key) && !o.get(key).isJsonNull()) ? o.get(key).getAsString() : "";
    }

    private void addStockDialog(long productId) {
        EditText input = new EditText(ctx);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint("Kitne piece aaye?");
        new AlertDialog.Builder(ctx)
        .setTitle(" Stock Add Karein")
        .setView(input)
        .setPositiveButton("Add", (d, w) -> {
            int qty = com.revenueaccount.app.utils.NumUtils.parseInt(input.getText().toString());
            if (qty <= 0) {
                AppToast.show(ctx, "Enter a valid quantity");
                return;
            }
            Map<String, Integer> body = new HashMap<>();
            body.put("quantity", qty);
            ApiClient.get(ctx).addStock(productId, body).enqueue(new Callback<JsonObject>() {
                @Override
                public void onResponse(@NonNull Call<JsonObject> c, @NonNull Response<JsonObject> r) {
                    AppToast.show(ctx, " Stock updated");
                    onChanged.refresh();
                }
                @Override
                public void onFailure(@NonNull Call<JsonObject> c, @NonNull Throwable t) {
                    AppToast.show(ctx, "Network error");
                }
            });
        })
        .setNegativeButton("Cancel", null)
        .show();
    }

    @Override
    public int getItemCount() { return data.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView name, meta, price, qty, stock;
        Button btnAdd;
        VH(View v) {
            super(v);
            name = v.findViewById(R.id.tvName);
            meta = v.findViewById(R.id.tvMeta);
            price = v.findViewById(R.id.tvPrice);
            qty = v.findViewById(R.id.tvQty);
            stock = v.findViewById(R.id.tvStock);
            btnAdd = v.findViewById(R.id.btnAddStock);
        }
    }
}
