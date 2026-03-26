package com.campus.outfit.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.outfit.entity.Outfit;
import com.campus.outfit.entity.OutfitTopic;
import com.campus.outfit.entity.Topic;
import com.campus.outfit.mapper.OutfitTopicMapper;
import com.campus.outfit.mapper.TopicMapper;
import com.campus.outfit.service.OutfitService;
import com.campus.outfit.service.TopicService;
import com.campus.outfit.utils.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class TopicServiceImpl extends ServiceImpl<TopicMapper, Topic> implements TopicService {

    @Autowired
    private OutfitTopicMapper outfitTopicMapper;

    @Autowired
    private OutfitService outfitService;

    @Override
    public Result<String> createTopic(Topic topic) {
        if (getOne(new LambdaQueryWrapper<Topic>().eq(Topic::getName, topic.getName())) != null) {
            return Result.fail("话题已存在");
        }
        topic.setOutfitCount(0);
        save(topic);
        return Result.success("话题创建成功");
    }

    @Override
    public List<Topic> getAllHotTopics() {
        return list(new LambdaQueryWrapper<Topic>()
                .orderByDesc(Topic::getOutfitCount)
                .last("LIMIT 10"));
    }

    @Override
    public IPage<Outfit> getOutfitsByTopic(Long topicId, int page, int size) {
        List<OutfitTopic> relations = outfitTopicMapper.selectList(new LambdaQueryWrapper<OutfitTopic>()
                .eq(OutfitTopic::getTopicId, topicId));
        List<Long> outfitIds = relations.stream().map(OutfitTopic::getOutfitId).collect(Collectors.toList());
        
        Page<Outfit> outfitPage = new Page<>(page, size);
        if (outfitIds.isEmpty()) {
            return outfitPage;
        }
        
        return outfitService.page(outfitPage, new LambdaQueryWrapper<Outfit>()
                .in(Outfit::getId, outfitIds)
                .eq(Outfit::getIsPublic, true)
                .eq(Outfit::getStatus, "PUBLISHED")
                .orderByDesc(Outfit::getCreateTime));
    }

    @Override
    public void incrementOutfitCount(Long topicId) {
        update(new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Topic>()
                .eq(Topic::getId, topicId)
                .setSql("outfit_count = outfit_count + 1"));
    }

    @Override
    public void decrementOutfitCount(Long topicId) {
        update(new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Topic>()
                .eq(Topic::getId, topicId)
                .setSql("outfit_count = GREATEST(outfit_count - 1, 0)"));
    }
}
