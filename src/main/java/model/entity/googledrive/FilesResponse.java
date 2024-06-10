package model.entity.googledrive;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class FilesResponse {
    @SerializedName("files")
    private List<File> files;

    public List<File> getFiles() {
        return files;
    }
}
