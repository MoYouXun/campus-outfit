package com.campus.outfit.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campus.outfit.entity.Outfit;
import com.campus.outfit.entity.Topic;
import com.campus.outfit.service.*;
import com.campus.outfit.utils.Result;
import com.campus.outfit.vo.OutfitVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/community")
public class CommunityController {

    @Autowired
    private OutfitService outfitService;

    @Autowired
    private TopicService topicService;

    @Autowired
    private LikeService likeService;

    @Autowired
    private FavoriteService favoriteService;

    @Autowired
    private FollowService followService;

    @Autowired
    private com.campus.outfit.service.CommentService commentService;

    @Autowired
    private com.campus.outfit.service.UserService userService;

    @GetMapping("/feed")
    public Result<IPage<OutfitVO>> getFeed(@RequestParam(defaultValue = "1") int page, 
                                        @RequestParam(defaultValue = "10") int size, 
                                        @RequestParam(defaultValue = "new") String sortBy,
                                        @RequestParam(required = false) Long topicId,
                                        @RequestParam(required = false) Long currentUserId) {
        return Result.success(outfitService.getPublicOutfits(page, size, sortBy, topicId, null, currentUserId));
    }

    @GetMapping("/following")
    public Result<IPage<OutfitVO>> getFollowingFeed(@RequestParam Long currentUserId,
                                                 @RequestParam(defaultValue = "1") int page,
                                                 @RequestParam(defaultValue = "10") int size) {
        IPage<com.campus.outfit.entity.Follow> followings = followService.getFollowings(currentUserId, 1, 1000);
        List<Long> followingIds = followings.getRecords().stream()
                .map(com.campus.outfit.entity.Follow::getFolloweeId)
                .collect(java.util.stream.Collectors.toList());
        return Result.success(outfitService.getFollowingOutfits(followingIds, page, size, currentUserId));
    }

    @GetMapping("/outfit/{id}")
    public Result<com.campus.outfit.dto.OutfitDetailDto> getDetail(@PathVariable Long id, @RequestParam(required = false) Long currentUserId) {
        Outfit outfit = outfitService.getById(id);
        if (outfit == null) return Result.fail("内容未找到");

        com.campus.outfit.dto.OutfitDetailDto dto = new com.campus.outfit.dto.OutfitDetailDto();
        dto.setOutfit(outfit);
        dto.setAuthor(userService.getById(outfit.getUserId()));
        dto.setComments(commentService.getCommentsByOutfit(id));
        
        if (currentUserId != null) {
            dto.setLiked(likeService.isLiked(currentUserId, id));
            dto.setFavorited(favoriteService.isFavorited(currentUserId, id));
            dto.setFollowingAuthor(followService.isFollowing(currentUserId, outfit.getUserId()));
        }
        
        return Result.success(dto);
    }

    @GetMapping("/topics")
    public Result<List<Topic>> getHotTopics() {
        return Result.success(topicService.getAllHotTopics());
    }
}
