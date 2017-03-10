
package com.android.easyChat.home;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.android.easyChat.R;
import com.android.easyChat.util.Constant;
import com.android.easyChat.util.FileName;

import android.app.ListActivity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.TextView;

public class MyFileManager extends ListActivity {
    private List<FileName> filePaths = new ArrayList<FileName>();// 保存当前目录下的所有文件的文件路径
    private String rootPath = "/";// 根目录路径
    private String parentPath = "/";// 初始化上级目录路径
    private Button returnRootBtn = null;
    private Button returnParentBtn = null;
    private ArrayList<FileName> selectedFilePath = new ArrayList<FileName>();// 保存被选择的所有文件路径
    private TextView mPath;// 用来显示当前目录路径
    private String currentPath = null;// 当前路径
    private int selectType = 0;
    private MyFileAdapter adapter = null;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.fileselect_layout);

        Intent intent = getIntent();
        selectType = intent.getExtras().getInt("selectType");

        Button buttonConfirm = (Button) findViewById(R.id.buttonConfirm);
        buttonConfirm.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                Intent intent = new Intent();
                if (selectType == Constant.SELECT_FILES) {// 如果当前为选择文件模式则返回当前选择的所有文件
                    intent.putExtra("selectType", Constant.SELECT_FILES);
                    intent.putExtra("files", selectedFilePath);
                } else if (selectType == Constant.SELECT_FILE_PATH) {// 如果当前为文件夹选择模式则返回当前选择的文件夹路径
                    File file = new File(currentPath);
                    intent.putExtra("selectType", Constant.SELECT_FILE_PATH);
                    if (file.canWrite()) {
                        intent.putExtra("fileSavePath", currentPath);
                    }
                }
                setResult(RESULT_OK, intent);
                finish();
            }
        });
        Button buttonCancle = (Button) findViewById(R.id.buttonCancle);
        buttonCancle.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                finish();
            }
        });

        returnRootBtn = (Button) findViewById(R.id.return_root_path);
        returnRootBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View returnRootBtn) {
                getFileDir(rootPath);
            }
        });
        returnParentBtn = (Button) findViewById(R.id.return_parent_path);
        returnParentBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View returnParentBtn) {
                getFileDir(parentPath);
            }
        });

        mPath = (TextView) findViewById(R.id.mPath);
        TextView title = (TextView) findViewById(R.id.file_select_title);
        if (selectType == Constant.SELECT_FILE_PATH) {
            title.setText(getString(R.string.select_path_for_save));
        } else {
            title.setText(getString(R.string.select_file_for_send));
        }

        getFileDir(rootPath);
    }

    private void getFileDir(String filePath) {
        if (null == filePath)
            return;// 检测是不是超出了根目录
        File dirFile = new File(filePath);
        parentPath = dirFile.getParent();// 获得当前目录的父目录
        File[] files = dirFile.listFiles();// 提取当前目录下的所有文件
        if (null != files) {
            filePaths.clear();
            selectedFilePath.clear();
            currentPath = filePath;
            Constant.fileSelectedState.clear();
            mPath.setText(getString(R.string.current_path_label) + filePath);
            for (File file : files) {
                if (selectType == Constant.SELECT_FILE_PATH) {// 如果选择模式为文件夹模式则只获得文件夹
                    if (file.isDirectory()) {
                        FileName fPath = new FileName(1, file.getPath());
                        filePaths.add(fPath);
                    }
                } else {// 如果选择模式为文件模式则获得所有文件夹与文件
                    if (file.isDirectory()) {
                        FileName fPath = new FileName(1, file.getPath());
                        filePaths.add(fPath);
                    } else {
                        FileName fPath = new FileName(2, file.getPath(), file.length(), false);
                        filePaths.add(fPath);
                    }
                }
            }
            Collections.sort(filePaths);// 进行排序，把文件夹排在前面，文件排在后面
            if (null == adapter) {
                adapter = new MyFileAdapter(this, filePaths);
            } else {
                adapter.setDatasource(filePaths);
            }
            setListAdapter(adapter);// 把获得的文件信息传给List适配器，让适配器更新列表条目
        }
    }

    @Override
    protected void onListItemClick(ListView listView, View itemView, int position, long id) {
        File file = new File(filePaths.get(position).fileName);// 获得在List中被点击的这个item所对应的文件
        if (file.isDirectory()) {// 如果该文件为目录文件则打开该目录
            getFileDir(filePaths.get(position).fileName);
        } else {// 如果该文件是一个普通文件则修改该条中选择框的状态，即选中该文件或取消选中
            CheckBox cb = (CheckBox) itemView.findViewById(R.id.file_selected);
            cb.setChecked(!cb.isChecked());// 选择该文件或取消选择
            onCheck(cb);// 传给onCheck方法继续处理
        }
    }

    // 检查检测框架的状态，根据该状态来保存或删除文件信息
    public void onCheck(View fileSelectedCheckBox) {
        CheckBox cb = (CheckBox) fileSelectedCheckBox;
        int fileIndex = (Integer) cb.getTag();// 获得该检测框在文件列表中对应的序号，该序号与列表中该条目的序号一致
        Constant.fileSelectedState.put(fileIndex, cb.isChecked());
        if (cb.isChecked()) {// 如果是被选中则保存该序号对应的文件信息
            FileName fName = filePaths.get(fileIndex);
            if (!selectedFilePath.contains(fName))
                selectedFilePath.add(filePaths.get(fileIndex));
        } else {// 如果取消选中则从保存的文件信息中删除该序号对应的文件信息
            selectedFilePath.remove(filePaths.get(fileIndex));
        }
    }
}
