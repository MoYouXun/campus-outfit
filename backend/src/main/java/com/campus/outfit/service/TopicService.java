package com.campus.outfit.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.outfit.entity.Outfit;
import com.campus.outfit.entity.Topic;
import com.campus.outfit.utils.Result;

import java.util.List;

public interface TopicService extends IService<Topic> {
    Result<String> createTopic(Topic topic);
    List<Topic> getAllHotTopics();
    IPage<Outfit> getOutfitsByTopic(Long topicId, int page, int size);
    void incrementOutfitCount(Long topicId);
    void decrementOutfitCount(Long topicId);
}
