package com.campus.outfit.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.outfit.dto.AiAnalysisResult;
import com.campus.outfit.entity.Outfit;
import com.campus.outfit.utils.Result;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface OutfitService extends IService<Outfit> {
    IPage<Outfit> getPublicOutfits(int page, int size, String sortBy, Long topicId, Long targetUserId, Long currentUserId);
    Result<AiAnalysisResult> uploadAndAnalyze(List<MultipartFile> files);
    Result<String> publishOutfit(Outfit outfit);
    IPage<Outfit> getPublicOutfits(int page, int size);
    IPage<Outfit> getFollowingOutfits(java.util.List<Long> followingIds, int page, int size, Long currentUserId);
    IPage<Outfit> getMyOutfits(Long userId, int page, int size);
    Result<String> deleteOutfit(Long id, Long userId);
    Result<String> incrementViewCount(Long id);
    void refreshOutfitUrls(Outfit outfit);
    List<com.campus.outfit.vo.OutfitVO> getMyPrivateOutfits(Long userId);
    void updateOutfitStatus(Long outfitId, Long userId, String status);
    List<com.campus.outfit.vo.OutfitVO> getMyFavoriteOutfits(Long userId);
}
