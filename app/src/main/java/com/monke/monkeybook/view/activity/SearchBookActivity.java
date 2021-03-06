//Copyright (c) 2017. 章钦豪. All rights reserved.
package com.monke.monkeybook.view.activity;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.flexbox.FlexboxLayout;
import com.hwangjr.rxbus.RxBus;
import com.monke.monkeybook.R;
import com.monke.monkeybook.base.MBaseActivity;
import com.monke.monkeybook.bean.SearchBookBean;
import com.monke.monkeybook.bean.SearchHistoryBean;
import com.monke.monkeybook.help.ACache;
import com.monke.monkeybook.help.RxBusTag;
import com.monke.monkeybook.presenter.BookDetailPresenter;
import com.monke.monkeybook.presenter.SearchBookPresenter;
import com.monke.monkeybook.presenter.contract.SearchBookContract;
import com.monke.monkeybook.utils.SharedPreferencesUtil;
import com.monke.monkeybook.utils.SoftInputUtil;
import com.monke.monkeybook.view.adapter.SearchBookAdapter;
import com.monke.monkeybook.widget.refreshview.OnLoadMoreListener;
import com.monke.monkeybook.widget.refreshview.RefreshRecyclerView;

import java.util.List;
import java.util.Objects;

import butterknife.BindView;
import butterknife.ButterKnife;
import tyrantgit.explosionfield.ExplosionField;

public class SearchBookActivity extends MBaseActivity<SearchBookContract.Presenter> implements SearchBookContract.View {

    @BindView(R.id.searchView)
    SearchView searchView;
    @BindView(R.id.toolbar)
    Toolbar toolbar;
    @BindView(R.id.ll_search_history)
    LinearLayout llSearchHistory;
    @BindView(R.id.tv_search_history_clean)
    TextView tvSearchHistoryClean;
    @BindView(R.id.tfl_search_history)
    FlexboxLayout tflSearchHistory;
    @BindView(R.id.rfRv_search_books)
    RefreshRecyclerView rfRvSearchBooks;
    @BindView(R.id.fabSearchStop)
    FloatingActionButton fabSearchStop;

    private Menu menu;
    private ExplosionField explosionField;
    private SearchBookAdapter searchBookAdapter;
    private SearchView.SearchAutoComplete mSearchAutoComplete;
    private boolean showHistory;
    private boolean useMy716;
    private String searchKey;

    private final View.OnClickListener historyItemClick = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            SearchHistoryBean searchHistoryBean = (SearchHistoryBean) v.getTag();
            searchView.setQuery(searchHistoryBean.getContent(), true);
        }
    };

    private final View.OnLongClickListener historyItemLongClick = new View.OnLongClickListener() {
        @Override
        public boolean onLongClick(View view) {
            SearchHistoryBean searchHistoryBean = (SearchHistoryBean) view.getTag();
            explosionField.explode(view);
            mPresenter.cleanSearchHistory(searchHistoryBean);
            return true;
        }
    };

    public static void startByKey(Context context, String searchKey) {
        Intent intent = new Intent(context, SearchBookActivity.class);
        intent.putExtra("searchKey", searchKey);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    protected SearchBookContract.Presenter initInjector() {
        useMy716 = !Objects.equals(ACache.get(this).getAsString("useMy716"), "False");
        return new SearchBookPresenter(this, useMy716);
    }

    @Override
    protected void onCreateActivity() {
        setContentView(R.layout.activity_search_book);
    }

    @Override
    protected void initData() {
        explosionField = ExplosionField.attach2Window(this);
        searchBookAdapter = new SearchBookAdapter(this);
    }

    @SuppressLint("InflateParams")
    @Override
    protected void bindView() {
        ButterKnife.bind(this);
        this.setSupportActionBar(toolbar);
        setupActionBar();
        initSearchView();
        fabSearchStop.hide();
        llSearchHistory.setOnClickListener(null);
        rfRvSearchBooks.setRefreshRecyclerViewAdapter(searchBookAdapter, new LinearLayoutManager(this));

        View viewRefreshError = LayoutInflater.from(this).inflate(R.layout.view_searchbook_refresh_error, null);
        viewRefreshError.findViewById(R.id.tv_refresh_again).setOnClickListener(v -> {
            //刷新失败 ，重试
            mPresenter.initPage();
            rfRvSearchBooks.startRefresh();
            mPresenter.toSearchBooks(null, true);
        });
        rfRvSearchBooks.setNoDataAndrRefreshErrorView(LayoutInflater.from(this).inflate(R.layout.view_searchbook_no_data, null),
                viewRefreshError);

        searchBookAdapter.setItemClickListener((view, position) -> {
            Intent intent = new Intent(SearchBookActivity.this, BookDetailActivity.class);
            intent.putExtra("openFrom", BookDetailPresenter.FROM_SEARCH);
            intent.putExtra("data", searchBookAdapter.getItemData(position));
            startActivityByAnim(intent, android.R.anim.fade_in, android.R.anim.fade_out);
        });

        fabSearchStop.setOnClickListener(view -> {
            fabSearchStop.hide();
            mPresenter.stopSearch();
        });
    }

    //设置ToolBar
    private void setupActionBar() {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle(R.string.action_search);
        }
    }

    // 添加菜单
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_book_search_activity, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        this.menu = menu;
        upMenu();
        return super.onPrepareOptionsMenu(menu);
    }

    //菜单
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        switch (id) {
            case R.id.action_book_source_manage:
                BookSourceActivity.startThis(this);
                break;
            case R.id.action_my716:
                useMy716 = !useMy716;
                upMenu();
                mPresenter.setUseMy716(useMy716);
                ACache.get(this).put("useMy716", useMy716 ? "True" : "False");
                break;
            case R.id.action_donate:
                DonateActivity.startThis(this);
                break;
            case R.id.action_get_hb:
                DonateActivity.getZfbHb(this);
                break;
            case android.R.id.home:
                SoftInputUtil.hideIMM(this, getCurrentFocus());
                finish();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * 更新菜单
     */
    @Override
    public void upMenu() {
        if (menu == null) return;
        boolean getHb = Objects.equals(ACache.get(this).getAsString("getZfbHb"), "True");
        for (int i = 0; i < menu.size(); i++) {
            MenuItem item = menu.getItem(i);
            if (item.getItemId() == R.id.action_my716) {
                item.setChecked(useMy716);
            }
            if (item.getGroupId() == R.id.menu_jz) {
                item.setVisible(!getHb);
            } else if (item.getGroupId() == R.id.menu_yc_source) {
                item.setVisible(getHb);
            }
        }
    }

    private void initSearchView() {
        mSearchAutoComplete = searchView.findViewById(R.id.search_src_text);
        searchView.setQueryHint(getString(R.string.search_book_key));
        //获取到TextView的控件
        mSearchAutoComplete.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        mSearchAutoComplete.setPadding(15, 0, 0, 0);
        searchView.onActionViewExpanded();
        LinearLayout editFrame = searchView.findViewById(android.support.v7.appcompat.R.id.search_edit_frame);
        ImageView closeButton = searchView.findViewById(android.support.v7.appcompat.R.id.search_close_btn);
        ImageView goButton = searchView.findViewById(android.support.v7.appcompat.R.id.search_go_btn);
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) editFrame.getLayoutParams();
        params.setMargins(20, 0, 10, 0);
        editFrame.setLayoutParams(params);
        closeButton.setScaleX(0.9f);
        closeButton.setScaleY(0.9f);
        closeButton.setPadding(0,0,0,0);
        goButton.setPadding(0,0,0,0);
        searchView.setSubmitButtonEnabled(true);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                if (TextUtils.isEmpty(query))
                    return false;
                searchKey = query.trim();
                if (!searchKey.toLowerCase().startsWith("set:")) {
                    toSearch();
                    searchView.clearFocus();
                    return false;
                } else {
                    parseSecretCode(searchKey);
                    finish();
                    return false;
                }
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                if (!newText.toLowerCase().startsWith("set:")) {
                    mPresenter.querySearchHistory(newText);
                }
                return false;
            }
        });
        searchView.setOnQueryTextFocusChangeListener((view, b) -> {
            showHistory = b;
            if (!b && searchView.getQuery().toString().trim().equals("")) {
                finish();
            }
            if (showHistory) {
                fabSearchStop.hide();
                mPresenter.stopSearch();
            }
            openOrCloseHistory(showHistory);
        });
    }

    @Override
    protected void bindEvent() {
        tvSearchHistoryClean.setOnClickListener(v -> {
            for (int i = 0; i < tflSearchHistory.getChildCount(); i++) {
                explosionField.explode(tflSearchHistory.getChildAt(i));
            }
            mPresenter.cleanSearchHistory();
        });

        rfRvSearchBooks.setLoadMoreListener(new OnLoadMoreListener() {
            @Override
            public void startLoadMore() {
                fabSearchStop.show();
                mPresenter.toSearchBooks(null, false);
            }

            @Override
            public void loadMoreErrorTryAgain() {
                fabSearchStop.show();
                mPresenter.toSearchBooks(null, true);
            }
        });
    }

    @Override
    protected void firstRequest() {
        super.firstRequest();
        Intent intent = this.getIntent();
        searchBook(intent.getStringExtra("searchKey"));
    }

    @Override
    public void onPause() {
        super.onPause();
        showHistory = llSearchHistory.getVisibility() == View.VISIBLE;
    }

    @Override
    public void onResume() {
        super.onResume();
        openOrCloseHistory(showHistory);
    }

    @Override
    public void searchBook(String searchKey) {
        if (!TextUtils.isEmpty(searchKey)) {
            searchView.setQuery(searchKey, true);
            showHistory = false;
        } else {
            showHistory = true;
            mPresenter.querySearchHistory("");
        }
        openOrCloseHistory(showHistory);
    }

    private void parseSecretCode(String code) {
        code = code.toLowerCase().replaceAll("^\\s*set:", "").trim();
        String[] param = code.split("\\s+");
        String msg = null;
        boolean enable = param.length == 1 || !param[1].equals("false");
        switch (param[0]) {
            case "show_nav_shelves":
                SharedPreferencesUtil.saveData("showNavShelves", enable);
                msg = "已" + (enable ? "启" : "禁") + "用侧边栏书架！";
                RxBus.get().post(RxBusTag.UPDATE_PX, true);
                break;
            case "fade_tts":
                SharedPreferencesUtil.saveData("fadeTTS", enable);
                msg = "已" + (enable ? "启" : "禁") + "用朗读时淡入淡出！";
                break;
            case "use_regex_in_new_rule":
                SharedPreferencesUtil.saveData("useRegexInNewRule", enable);
                msg = "已" + (enable ? "启" : "禁") + "用新建替换规则时默认使用正则表达式！";
                break;
            case "blur_sim_back":
                SharedPreferencesUtil.saveData("blurSimBack", enable);
                msg = "已" + (enable ? "启" : "禁") + "用仿真翻页背景虚化！";
                break;
            case "async_draw":
                SharedPreferencesUtil.saveData("asyncDraw", enable);
                msg = "已" + (enable ? "启" : "禁") + "用异步加载！";
                break;
            case "disable_scroll_click_turn":
                SharedPreferencesUtil.saveData("disableScrollClickTurn", enable);
                msg = "已" + (enable ? "禁" : "启") + "用滚动模式点击翻页！";
                break;
        }
        if (msg == null) {
            toast("无法识别设置密码: " + code, 0, -1);
        } else {
            toast(msg, 0, 1);
        }
    }

    /**
     * 开始搜索
     */
    private void toSearch() {
        if (!TextUtils.isEmpty(searchKey)) {
            mPresenter.insertSearchHistory();
            //执行搜索请求
            new Handler().postDelayed(() -> {
                mPresenter.initPage();
                rfRvSearchBooks.startRefresh();
                fabSearchStop.show();
                mPresenter.toSearchBooks(searchKey, false);
            }, 300);
        }
    }

    private void openOrCloseHistory(Boolean open) {
        if (open) {
            if (llSearchHistory.getVisibility() != View.VISIBLE) {
                llSearchHistory.setVisibility(View.VISIBLE);
            }
        } else {
            if (llSearchHistory.getVisibility() == View.VISIBLE) {
                llSearchHistory.setVisibility(View.GONE);
            }
        }
    }

    private void addNewHistories(List<SearchHistoryBean> historyBeans) {
        tflSearchHistory.removeAllViews();
        if (historyBeans != null) {
            TextView tagView;
            for (SearchHistoryBean searchHistoryBean : historyBeans) {
                tagView = (TextView) getLayoutInflater().inflate(R.layout.item_search_history, tflSearchHistory, false);
                tagView.setTag(searchHistoryBean);
                tagView.setText(searchHistoryBean.getContent());
                tagView.setOnClickListener(historyItemClick);
                tagView.setOnLongClickListener(historyItemLongClick);
                tflSearchHistory.addView(tagView);
            }
        }
    }

    @Override
    public void insertSearchHistorySuccess(SearchHistoryBean searchHistoryBean) {
        //搜索历史插入或者修改成功
        mPresenter.querySearchHistory(searchKey);
    }

    @Override
    public void querySearchHistorySuccess(List<SearchHistoryBean> datas) {
        addNewHistories(datas);
        if (tflSearchHistory.getChildCount() > 0) {
            tvSearchHistoryClean.setVisibility(View.VISIBLE);
        } else {
            tvSearchHistoryClean.setVisibility(View.INVISIBLE);
        }
    }

    @Override
    public void refreshSearchBook() {
        searchBookAdapter.clearAll();
    }

    @Override
    public void refreshFinish(Boolean isAll) {
        fabSearchStop.hide();
        rfRvSearchBooks.finishRefresh(isAll, true);
    }

    @Override
    public void loadMoreFinish(Boolean isAll) {
        fabSearchStop.hide();
        rfRvSearchBooks.finishLoadMore(isAll, true);
    }

    @Override
    public void searchBookError(Boolean isRefresh) {
        if (isRefresh) {
            rfRvSearchBooks.refreshError();
        } else {
            rfRvSearchBooks.loadMoreError();
        }
    }

    @Override
    public void loadMoreSearchBook(final List<SearchBookBean> books) {
        searchBookAdapter.addAll(books, mSearchAutoComplete.getText().toString().trim());
    }

    @Override
    protected void onDestroy() {
        mPresenter.stopSearch();
        explosionField.clear();
        super.onDestroy();
    }

    @Override
    public EditText getEdtContent() {
        return mSearchAutoComplete;
    }

    @Override
    public SearchBookAdapter getSearchBookAdapter() {
        return searchBookAdapter;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

    }
}
