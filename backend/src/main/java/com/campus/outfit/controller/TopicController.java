package com.campus.outfit.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campus.outfit.entity.Outfit;
import com.campus.outfit.entity.Topic;
import com.campus.outfit.service.TopicService;
import com.campus.outfit.utils.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/topic")
public class TopicController {

    @Autowired
    private TopicService topicService;

    @GetMapping
    public Result<List<Topic>> getAllTopics() {
        return Result.success(topicService.list());
    }

    @GetMapping("/{id}/outfits")
    public Result<IPage<Outfit>> getOutfitsByTopic(@PathVariable Long id, 
                                                  @RequestParam(defaultValue = "1") int page, 
                                                  @RequestParam(defaultValue = "10") int size) {
        return Result.success(topicService.getOutfitsByTopic(id, page, size));
    }
}
